//! Sprout Pals friends server.
//!
//! Lets people with the app find each other by a 6-letter GARDEN CODE, see each other's pets,
//! and send hearts. No accounts or passwords: on first launch the app registers and gets a
//! random secret token, kept on the phone; every later call sends it as `Authorization: Bearer`.
//!
//! Stores only what the game needs: the garden code, the pet's species and stage, friendships
//! and hearts. No names, emails or locations.
//!
//! Runs behind Caddy at https://jasonagibson.zapto.org/game/api/ (no new open ports).
//! Env: SPROUT_DB (default sprout.db), PORT (default 8020), BIND (default 0.0.0.0).

mod api;
mod store;

use std::net::SocketAddr;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(std::env::var("RUST_LOG").unwrap_or_else(|_| "sprout_server=info".into()))
        .init();
    let db = std::env::var("SPROUT_DB").unwrap_or_else(|_| "sprout.db".into());
    let port: u16 = std::env::var("PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(8020);

    let store = store::Store::open(&format!("sqlite://{db}?mode=rwc")).await.expect("open database");
    let app = api::router(store);

    // BIND=127.0.0.1 for a local test on a PC; the container on the NUC listens on all of its own interfaces
    let ip: std::net::IpAddr = std::env::var("BIND").ok().and_then(|b| b.parse().ok()).unwrap_or([0, 0, 0, 0].into());
    let addr = SocketAddr::new(ip, port);
    tracing::info!("sprout-server listening on {addr}");
    let listener = tokio::net::TcpListener::bind(addr).await.expect("bind");
    axum::serve(listener, app)
        .with_graceful_shutdown(async { let _ = tokio::signal::ctrl_c().await; })
        .await
        .expect("serve");
}
