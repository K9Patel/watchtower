-- ================================================================
--  WatchTower — V10 Authentication Schema
--  Creates users + password_reset_tokens tables for JWT auth
-- ================================================================

-- ── 1. USERS ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id                     VARCHAR(26)     PRIMARY KEY,
    user_name                   VARCHAR(255)    NOT NULL,
    user_email                  VARCHAR(255)    NOT NULL UNIQUE,
    user_password               VARCHAR(255)    NOT NULL,
    email_verified_at           TIMESTAMP       NULL,
    verification_token          VARCHAR(255)    NULL,
    verification_code_expires_at TIMESTAMP      NULL,
    google_id                   VARCHAR(255)    NULL UNIQUE,
    avatar                      VARCHAR(255)    NULL,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (user_email);

-- ── 2. PASSWORD RESET TOKENS ─────────────────────────────────
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    email       VARCHAR(255)    PRIMARY KEY,
    token       VARCHAR(255)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ── END V10 ─────────────────────────────────────────────────
