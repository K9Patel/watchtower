-- ================================================================
--  WatchTower — V13 User Network Scope
--  Per-user, per-network history boundaries.
-- ================================================================

CREATE TABLE IF NOT EXISTS user_network_scope (
    id             BIGSERIAL PRIMARY KEY,
    user_id        VARCHAR(26) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    network_prefix VARCHAR(64) NOT NULL,
    started_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_seen_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_network_scope UNIQUE (user_id, network_prefix)
);

CREATE INDEX IF NOT EXISTS idx_user_network_scope_user ON user_network_scope (user_id);
CREATE INDEX IF NOT EXISTS idx_user_network_scope_network ON user_network_scope (network_prefix);
