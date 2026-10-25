//! SQLite storage: players, friendships, hearts, visits.

use rand::RngExt;
use serde::Serialize;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePool, SqlitePoolOptions};
use sqlx::Row;
use std::str::FromStr;
use std::time::{SystemTime, UNIX_EPOCH};

/// Letters and digits that can't be misread when typed from a friend's screen (no I/O/0/1/L).
const CODE_ALPHABET: &[u8] = b"ABCDEFGHJKMNPQRSTUVWXYZ23456789";
pub const CODE_LEN: usize = 6;

pub fn now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

#[derive(Clone)]
pub struct Store {
    pool: SqlitePool,
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Player {
    #[serde(skip)]
    pub id: i64,
    pub code: String,
    pub species: String,
    pub stage: String,
    #[serde(rename = "lastSeen")]
    pub last_seen: i64,
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Visitor {
    pub code: String,
    pub species: String,
    pub stage: String,
    pub outfit: String,
    pub until: i64,
}

/// Our pal's trip: whose garden, until when, and the host's own pal (to show them together).
#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Away {
    pub host: String,
    pub until: i64,
    pub species: String,
    pub stage: String,
    pub outfit: String,
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Heart {
    pub from: String,
    pub message: String,
    pub at: i64,
}

impl Store {
    pub async fn open(url: &str) -> sqlx::Result<Self> {
        let opts = SqliteConnectOptions::from_str(url)?.create_if_missing(true).foreign_keys(true);
        let pool = SqlitePoolOptions::new().max_connections(4).connect_with(opts).await?;
        let store = Store { pool };
        store.migrate().await?;
        Ok(store)
    }

    async fn migrate(&self) -> sqlx::Result<()> {
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS players (
                id INTEGER PRIMARY KEY,
                code TEXT NOT NULL UNIQUE,
                token TEXT NOT NULL UNIQUE,
                species TEXT NOT NULL DEFAULT 'EGG',
                stage TEXT NOT NULL DEFAULT 'EGG',
                created INTEGER NOT NULL,
                last_seen INTEGER NOT NULL
            )",
        )
        .execute(&self.pool)
        .await?;
        // one row per direction, so "my friends" is a single lookup
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS friends (
                player INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                friend INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                since INTEGER NOT NULL,
                PRIMARY KEY (player, friend)
            )",
        )
        .execute(&self.pool)
        .await?;
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS hearts (
                id INTEGER PRIMARY KEY,
                sender INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                recipient INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                message TEXT NOT NULL,
                at INTEGER NOT NULL,
                delivered INTEGER NOT NULL DEFAULT 0
            )",
        )
        .execute(&self.pool)
        .await?;
        sqlx::query("CREATE INDEX IF NOT EXISTS hearts_inbox ON hearts(recipient, delivered)")
            .execute(&self.pool)
            .await?;
        // a pal staying in a friend's garden for a few hours
        sqlx::query(
            "CREATE TABLE IF NOT EXISTS visits (
                id INTEGER PRIMARY KEY,
                guest INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                host INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                start INTEGER NOT NULL,
                until INTEGER NOT NULL
            )",
        )
        .execute(&self.pool)
        .await?;
        sqlx::query("CREATE INDEX IF NOT EXISTS visits_host ON visits(host, until)").execute(&self.pool).await?;
        // what the pal is wearing, so it arrives dressed up (added after the first release)
        let has_outfit = sqlx::query("SELECT 1 FROM pragma_table_info('players') WHERE name = 'outfit'")
            .fetch_optional(&self.pool)
            .await?
            .is_some();
        if !has_outfit {
            sqlx::query("ALTER TABLE players ADD COLUMN outfit TEXT NOT NULL DEFAULT ''").execute(&self.pool).await?;
        }
        Ok(())
    }

    pub async fn set_outfit(&self, id: i64, outfit: &str) -> sqlx::Result<()> {
        sqlx::query("UPDATE players SET outfit = ? WHERE id = ?").bind(outfit).bind(id).execute(&self.pool).await?;
        Ok(())
    }

    /// The guest's visit that's still going, if any (one visit at a time).
    pub async fn current_visit(&self, guest: i64) -> sqlx::Result<Option<Away>> {
        let r = sqlx::query(
            "SELECT p.code, v.until, p.species, p.stage, p.outfit FROM visits v JOIN players p ON p.id = v.host
             WHERE v.guest = ? AND v.until > ? ORDER BY v.until DESC LIMIT 1",
        )
        .bind(guest)
        .bind(now())
        .fetch_optional(&self.pool)
        .await?;
        Ok(r.map(|r| Away { host: r.get(0), until: r.get(1), species: r.get(2), stage: r.get(3), outfit: r.get(4) }))
    }

    pub async fn start_visit(&self, guest: i64, host: i64, hours: i64) -> sqlx::Result<i64> {
        let t = now();
        let until = t + hours * 3600;
        sqlx::query("INSERT INTO visits (guest, host, start, until) VALUES (?, ?, ?, ?)")
            .bind(guest)
            .bind(host)
            .bind(t)
            .bind(until)
            .execute(&self.pool)
            .await?;
        Ok(until)
    }

    /// Pals visiting this garden right now (at most [limit]), earliest arrival first.
    pub async fn visitors(&self, host: i64, limit: i64) -> sqlx::Result<Vec<Visitor>> {
        let rows = sqlx::query(
            "SELECT p.code, p.species, p.stage, p.outfit, v.until FROM visits v JOIN players p ON p.id = v.guest
             WHERE v.host = ? AND v.until > ? ORDER BY v.start LIMIT ?",
        )
        .bind(host)
        .bind(now())
        .bind(limit)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.iter().map(|r| Visitor { code: r.get(0), species: r.get(1), stage: r.get(2), outfit: r.get(3), until: r.get(4) }).collect())
    }

    /// New player: a fresh garden code and a secret token (64 hex chars). `rand::rng()` is a
    /// cryptographically secure generator seeded (and reseeded) from the operating system.
    pub async fn register(&self) -> sqlx::Result<(String, String)> {
        let token: String = {
            let mut rng = rand::rng();
            (0..32).map(|_| format!("{:02x}", rng.random::<u8>())).collect()
        };
        // retry on the (tiny) chance of a code collision
        for _ in 0..10 {
            let code = new_code();
            let t = now();
            let res = sqlx::query("INSERT INTO players (code, token, created, last_seen) VALUES (?, ?, ?, ?)")
                .bind(&code)
                .bind(&token)
                .bind(t)
                .bind(t)
                .execute(&self.pool)
                .await;
            match res {
                Ok(_) => return Ok((code, token)),
                Err(sqlx::Error::Database(e)) if e.is_unique_violation() => continue,
                Err(e) => return Err(e),
            }
        }
        Err(sqlx::Error::Protocol("could not pick a free garden code".into()))
    }

    pub async fn by_token(&self, token: &str) -> sqlx::Result<Option<Player>> {
        let row = sqlx::query("SELECT id, code, species, stage, last_seen FROM players WHERE token = ?")
            .bind(token)
            .fetch_optional(&self.pool)
            .await?;
        let Some(r) = row else { return Ok(None) };
        let p = player(&r);
        sqlx::query("UPDATE players SET last_seen = ? WHERE id = ?").bind(now()).bind(p.id).execute(&self.pool).await?;
        Ok(Some(p))
    }

    pub async fn by_code(&self, code: &str) -> sqlx::Result<Option<Player>> {
        let row = sqlx::query("SELECT id, code, species, stage, last_seen FROM players WHERE code = ?")
            .bind(code)
            .fetch_optional(&self.pool)
            .await?;
        Ok(row.as_ref().map(player))
    }

    pub async fn set_pet(&self, id: i64, species: &str, stage: &str) -> sqlx::Result<()> {
        sqlx::query("UPDATE players SET species = ?, stage = ? WHERE id = ?")
            .bind(species)
            .bind(stage)
            .bind(id)
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    /// Friends both ways. Adding someone who's already a friend is fine (no error, no duplicate).
    pub async fn befriend(&self, a: i64, b: i64) -> sqlx::Result<()> {
        let t = now();
        for (p, f) in [(a, b), (b, a)] {
            sqlx::query("INSERT OR IGNORE INTO friends (player, friend, since) VALUES (?, ?, ?)")
                .bind(p)
                .bind(f)
                .bind(t)
                .execute(&self.pool)
                .await?;
        }
        Ok(())
    }

    pub async fn friend_count(&self, id: i64) -> sqlx::Result<i64> {
        sqlx::query("SELECT COUNT(*) FROM friends WHERE player = ?").bind(id).fetch_one(&self.pool).await.map(|r| r.get(0))
    }

    pub async fn are_friends(&self, a: i64, b: i64) -> sqlx::Result<bool> {
        let r = sqlx::query("SELECT 1 FROM friends WHERE player = ? AND friend = ?")
            .bind(a)
            .bind(b)
            .fetch_optional(&self.pool)
            .await?;
        Ok(r.is_some())
    }

    pub async fn friends(&self, id: i64) -> sqlx::Result<Vec<Player>> {
        let rows = sqlx::query(
            "SELECT p.id, p.code, p.species, p.stage, p.last_seen FROM friends f
             JOIN players p ON p.id = f.friend WHERE f.player = ? ORDER BY f.since",
        )
        .bind(id)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.iter().map(player).collect())
    }

    pub async fn hearts_sent_since(&self, sender: i64, since: i64) -> sqlx::Result<i64> {
        sqlx::query("SELECT COUNT(*) FROM hearts WHERE sender = ? AND at >= ?")
            .bind(sender)
            .bind(since)
            .fetch_one(&self.pool)
            .await
            .map(|r| r.get(0))
    }

    pub async fn send_heart(&self, from: i64, to: i64, message: &str) -> sqlx::Result<()> {
        sqlx::query("INSERT INTO hearts (sender, recipient, message, at) VALUES (?, ?, ?, ?)")
            .bind(from)
            .bind(to)
            .bind(message)
            .bind(now())
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    /// Undelivered hearts, oldest first; marks them delivered so each one shows once.
    pub async fn take_inbox(&self, id: i64) -> sqlx::Result<Vec<Heart>> {
        let mut tx = self.pool.begin().await?;
        let rows = sqlx::query(
            "SELECT h.id, p.code, h.message, h.at FROM hearts h JOIN players p ON p.id = h.sender
             WHERE h.recipient = ? AND h.delivered = 0 ORDER BY h.at LIMIT 50",
        )
        .bind(id)
        .fetch_all(&mut *tx)
        .await?;
        for r in &rows {
            sqlx::query("UPDATE hearts SET delivered = 1 WHERE id = ?").bind(r.get::<i64, _>(0)).execute(&mut *tx).await?;
        }
        tx.commit().await?;
        Ok(rows.iter().map(|r| Heart { from: r.get(1), message: r.get(2), at: r.get(3) }).collect())
    }
}

fn player(r: &sqlx::sqlite::SqliteRow) -> Player {
    Player { id: r.get(0), code: r.get(1), species: r.get(2), stage: r.get(3), last_seen: r.get(4) }
}

fn new_code() -> String {
    let mut rng = rand::rng();
    (0..CODE_LEN).map(|_| CODE_ALPHABET[rng.random_range(0..CODE_ALPHABET.len())] as char).collect()
}

/// Accepts what people actually type: lower case, spaces, dashes. Returns None if it can't be a code.
pub fn normalize_code(input: &str) -> Option<String> {
    let c: String = input.chars().filter(|c| c.is_ascii_alphanumeric()).map(|c| c.to_ascii_uppercase()).collect();
    (c.len() == CODE_LEN && c.bytes().all(|b| CODE_ALPHABET.contains(&b))).then_some(c)
}
