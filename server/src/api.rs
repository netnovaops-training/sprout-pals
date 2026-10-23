//! HTTP routes, all under /api (Caddy forwards /game/api/* here with the /game prefix removed).
//!
//!   POST /api/register              -> {code, token}               (new player)
//!   GET  /api/me                    -> {code, species, stage, lastSeen}
//!   PUT  /api/pet   {species, stage}                                (what your pet is now)
//!   POST /api/friends {code}        -> {code, species, stage, lastSeen}   (add by garden code)
//!   GET  /api/friends               -> [friend...]
//!   POST /api/hearts {code, message}                                (to a friend)
//!   GET  /api/inbox                 -> [{from, message, at}]       (each heart delivered once)

use crate::store::{normalize_code, now, Player, Store};
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post, put};
use axum::{Json, Router};
use serde::Deserialize;
use serde_json::json;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

/// Kept small on purpose: these are friendly limits for a family game, not a public network.
const MAX_FRIENDS: i64 = 50;
const HEARTS_PER_DAY: i64 = 100;
const REGISTER_PER_HOUR_PER_IP: usize = 10;
const MESSAGES: &[&str] = &["", "hi!", "miss you", "good morning", "good night", "let's play", "thank you", "proud of you"];
const SPECIES: &[&str] = &["EGG", "JUN", "KONA", "RUSTY", "KARA", "DJ", "MAUI", "JASON"];
const STAGES: &[&str] = &["EGG", "BABY", "KID", "ADULT", "OLD", "GONE"];

#[derive(Clone)]
pub struct AppState {
    store: Store,
    registrations: Arc<Mutex<HashMap<String, Vec<i64>>>>,
}

pub fn router(store: Store) -> Router {
    let state = AppState { store, registrations: Arc::new(Mutex::new(HashMap::new())) };
    Router::new()
        .route("/api/health", get(|| async { "ok" }))
        .route("/api/register", post(register))
        .route("/api/me", get(me))
        .route("/api/pet", put(set_pet))
        .route("/api/friends", post(add_friend).get(list_friends))
        .route("/api/hearts", post(send_heart))
        .route("/api/inbox", get(inbox))
        .with_state(state)
}

/// Errors are a short sentence the app can show as-is.
pub struct ApiError(StatusCode, &'static str);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({ "error": self.1 }))).into_response()
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(e: sqlx::Error) -> Self {
        tracing::error!("database: {e}");
        ApiError(StatusCode::INTERNAL_SERVER_ERROR, "Something went wrong. Try again in a bit.")
    }
}

type ApiResult<T> = Result<T, ApiError>;

async fn caller(state: &AppState, headers: &HeaderMap) -> ApiResult<Player> {
    let token = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(ApiError(StatusCode::UNAUTHORIZED, "Not signed in."))?;
    state.store.by_token(token).await?.ok_or(ApiError(StatusCode::UNAUTHORIZED, "Not signed in."))
}

/// The visitor's address as Caddy saw it. The server only listens behind Caddy, which always
/// sets X-Forwarded-For; anything without it shares one "unknown" bucket.
fn client_ip(headers: &HeaderMap) -> String {
    headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "unknown".into())
}

async fn register(State(state): State<AppState>, headers: HeaderMap) -> ApiResult<Json<serde_json::Value>> {
    let ip = client_ip(&headers);
    {
        let mut seen = state.registrations.lock().unwrap();
        let hour_ago = now() - 3600;
        let list = seen.entry(ip).or_default();
        list.retain(|t| *t > hour_ago);
        if list.len() >= REGISTER_PER_HOUR_PER_IP {
            return Err(ApiError(StatusCode::TOO_MANY_REQUESTS, "Too many new gardens from here. Try later."));
        }
        list.push(now());
    }
    let (code, token) = state.store.register().await?;
    Ok(Json(json!({ "code": code, "token": token })))
}

async fn me(State(state): State<AppState>, headers: HeaderMap) -> ApiResult<Json<Player>> {
    Ok(Json(caller(&state, &headers).await?))
}

#[derive(Deserialize)]
struct PetBody {
    species: String,
    stage: String,
}

async fn set_pet(State(state): State<AppState>, headers: HeaderMap, Json(b): Json<PetBody>) -> ApiResult<StatusCode> {
    let me = caller(&state, &headers).await?;
    if !SPECIES.contains(&b.species.as_str()) || !STAGES.contains(&b.stage.as_str()) {
        return Err(ApiError(StatusCode::BAD_REQUEST, "Unknown pet."));
    }
    state.store.set_pet(me.id, &b.species, &b.stage).await?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
struct CodeBody {
    code: String,
}

async fn add_friend(State(state): State<AppState>, headers: HeaderMap, Json(b): Json<CodeBody>) -> ApiResult<Json<Player>> {
    let me = caller(&state, &headers).await?;
    let code = normalize_code(&b.code).ok_or(ApiError(StatusCode::BAD_REQUEST, "A garden code is 6 letters and numbers."))?;
    if code == me.code {
        return Err(ApiError(StatusCode::BAD_REQUEST, "That's your own garden code!"));
    }
    let friend = state.store.by_code(&code).await?.ok_or(ApiError(StatusCode::NOT_FOUND, "No garden with that code."))?;
    if !state.store.are_friends(me.id, friend.id).await? && state.store.friend_count(me.id).await? >= MAX_FRIENDS {
        return Err(ApiError(StatusCode::BAD_REQUEST, "Your friends list is full."));
    }
    state.store.befriend(me.id, friend.id).await?;
    Ok(Json(friend))
}

async fn list_friends(State(state): State<AppState>, headers: HeaderMap) -> ApiResult<Json<Vec<Player>>> {
    let me = caller(&state, &headers).await?;
    Ok(Json(state.store.friends(me.id).await?))
}

#[derive(Deserialize)]
struct HeartBody {
    code: String,
    #[serde(default)]
    message: String,
}

async fn send_heart(State(state): State<AppState>, headers: HeaderMap, Json(b): Json<HeartBody>) -> ApiResult<StatusCode> {
    let me = caller(&state, &headers).await?;
    // preset messages only: nothing typed travels between phones, so there's nothing to moderate
    if !MESSAGES.contains(&b.message.as_str()) {
        return Err(ApiError(StatusCode::BAD_REQUEST, "Pick one of the messages."));
    }
    let code = normalize_code(&b.code).ok_or(ApiError(StatusCode::BAD_REQUEST, "Unknown garden."))?;
    let to = state.store.by_code(&code).await?.ok_or(ApiError(StatusCode::NOT_FOUND, "Unknown garden."))?;
    if !state.store.are_friends(me.id, to.id).await? {
        return Err(ApiError(StatusCode::FORBIDDEN, "Add them as a friend first."));
    }
    if state.store.hearts_sent_since(me.id, now() - 86_400).await? >= HEARTS_PER_DAY {
        return Err(ApiError(StatusCode::TOO_MANY_REQUESTS, "That's a lot of love for one day. Try tomorrow!"));
    }
    state.store.send_heart(me.id, to.id, &b.message).await?;
    Ok(StatusCode::NO_CONTENT)
}

async fn inbox(State(state): State<AppState>, headers: HeaderMap) -> ApiResult<Json<serde_json::Value>> {
    let me = caller(&state, &headers).await?;
    Ok(Json(json!(state.store.take_inbox(me.id).await?)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::Request;
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    async fn app() -> Router {
        router(Store::open("sqlite::memory:").await.unwrap())
    }

    async fn call(app: &Router, method: &str, path: &str, token: Option<&str>, body: Option<serde_json::Value>) -> (StatusCode, serde_json::Value) {
        let mut req = Request::builder().method(method).uri(path).header("content-type", "application/json");
        if let Some(t) = token {
            req = req.header("authorization", format!("Bearer {t}"));
        }
        let req = req.body(body.map(|b| Body::from(b.to_string())).unwrap_or_else(Body::empty)).unwrap();
        let res = app.clone().oneshot(req).await.unwrap();
        let status = res.status();
        let bytes = res.into_body().collect().await.unwrap().to_bytes();
        let json = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
        (status, json)
    }

    async fn new_player(app: &Router) -> (String, String) {
        let (s, j) = call(app, "POST", "/api/register", None, None).await;
        assert_eq!(s, StatusCode::OK);
        (j["code"].as_str().unwrap().to_string(), j["token"].as_str().unwrap().to_string())
    }

    #[tokio::test]
    async fn friends_by_code_then_hearts() {
        let app = app().await;
        let (jason_code, jason) = new_player(&app).await;
        let (jun_code, jun) = new_player(&app).await;
        assert_eq!(jason_code.len(), 6);

        // Jun tells Jason the code; Jason types it in lower case with a dash
        let typed = format!("{}-{}", &jun_code[..3], &jun_code[3..]).to_lowercase();
        let (s, j) = call(&app, "POST", "/api/friends", Some(&jason), Some(json!({ "code": typed }))).await;
        assert_eq!(s, StatusCode::OK, "{j}");
        assert_eq!(j["code"], jun_code);

        // friendship goes both ways
        let (_, list) = call(&app, "GET", "/api/friends", Some(&jun), None).await;
        assert_eq!(list[0]["code"], jason_code);

        // pet shows up for friends
        let (s, _) = call(&app, "PUT", "/api/pet", Some(&jun), Some(json!({ "species": "KARA", "stage": "KID" }))).await;
        assert_eq!(s, StatusCode::NO_CONTENT);
        let (_, list) = call(&app, "GET", "/api/friends", Some(&jason), None).await;
        assert_eq!(list[0]["species"], "KARA");

        // a heart arrives once
        let (s, _) = call(&app, "POST", "/api/hearts", Some(&jason), Some(json!({ "code": jun_code, "message": "miss you" }))).await;
        assert_eq!(s, StatusCode::NO_CONTENT);
        let (_, inbox) = call(&app, "GET", "/api/inbox", Some(&jun), None).await;
        assert_eq!(inbox[0]["from"], jason_code);
        assert_eq!(inbox[0]["message"], "miss you");
        let (_, again) = call(&app, "GET", "/api/inbox", Some(&jun), None).await;
        assert_eq!(again.as_array().unwrap().len(), 0);
    }

    #[tokio::test]
    async fn the_guard_rails() {
        let app = app().await;
        let (my_code, me) = new_player(&app).await;
        let (stranger_code, _) = new_player(&app).await;

        let (s, _) = call(&app, "GET", "/api/friends", None, None).await;
        assert_eq!(s, StatusCode::UNAUTHORIZED);
        let (s, _) = call(&app, "GET", "/api/friends", Some("not-a-token"), None).await;
        assert_eq!(s, StatusCode::UNAUTHORIZED);

        let (s, _) = call(&app, "POST", "/api/friends", Some(&me), Some(json!({ "code": my_code }))).await;
        assert_eq!(s, StatusCode::BAD_REQUEST, "can't befriend yourself");
        let (s, _) = call(&app, "POST", "/api/friends", Some(&me), Some(json!({ "code": "ABC" }))).await;
        assert_eq!(s, StatusCode::BAD_REQUEST, "too short");
        let (s, _) = call(&app, "POST", "/api/friends", Some(&me), Some(json!({ "code": "ZZZZZZ" }))).await;
        assert_eq!(s, StatusCode::NOT_FOUND);

        // hearts only to friends, and only preset messages
        let (s, _) = call(&app, "POST", "/api/hearts", Some(&me), Some(json!({ "code": stranger_code, "message": "hi!" }))).await;
        assert_eq!(s, StatusCode::FORBIDDEN);
        call(&app, "POST", "/api/friends", Some(&me), Some(json!({ "code": stranger_code }))).await;
        let (s, _) = call(&app, "POST", "/api/hearts", Some(&me), Some(json!({ "code": stranger_code, "message": "anything I like" }))).await;
        assert_eq!(s, StatusCode::BAD_REQUEST);

        let (s, _) = call(&app, "PUT", "/api/pet", Some(&me), Some(json!({ "species": "DRAGON", "stage": "ADULT" }))).await;
        assert_eq!(s, StatusCode::BAD_REQUEST);
    }

    #[test]
    fn codes_are_forgiving_to_type() {
        assert_eq!(normalize_code(" abc-def "), Some("ABCDEF".into()));
        assert_eq!(normalize_code("ab cd 23"), Some("ABCD23".into()));
        assert_eq!(normalize_code("ABCDE0"), None, "0 is never used (looks like O)");
        assert_eq!(normalize_code("ABCDEFG"), None);
    }
}
