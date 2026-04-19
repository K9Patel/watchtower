-- ================================================================
--  WatchTower — V13 Network health score history
--  Stores periodic composite health snapshots for analytics.
-- ================================================================

CREATE TABLE IF NOT EXISTS health_score_log (
    id                  BIGSERIAL PRIMARY KEY,
    score               SMALLINT NOT NULL CHECK (score >= 0 AND score <= 100),
    bandwidth_score     SMALLINT NOT NULL CHECK (bandwidth_score >= 0 AND bandwidth_score <= 100),
    latency_score       SMALLINT NOT NULL CHECK (latency_score >= 0 AND latency_score <= 100),
    alert_score         SMALLINT NOT NULL CHECK (alert_score >= 0 AND alert_score <= 100),
    uptime_score        SMALLINT NOT NULL CHECK (uptime_score >= 0 AND uptime_score <= 100),
    trend               VARCHAR(12) NOT NULL DEFAULT 'STABLE',
    active_device_count SMALLINT NOT NULL,
    active_alert_count  SMALLINT NOT NULL,
    recorded_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_health_recorded ON health_score_log (recorded_at DESC);

-- ── END V13 ──────────────────────────────────────────────────────
