-- ================================================================
--  WatchTower — V14 Health Score Log
--  Network health metrics tracking over time
-- ================================================================

CREATE TABLE IF NOT EXISTS health_score_log (
    id                      BIGSERIAL           PRIMARY KEY,
    score                   SMALLINT            NOT NULL,
    bandwidth_score         SMALLINT            NOT NULL,
    latency_score           SMALLINT            NOT NULL,
    alert_score             SMALLINT            NOT NULL,
    uptime_score            SMALLINT            NOT NULL,
    trend                   VARCHAR(12)         NOT NULL DEFAULT 'STABLE',
    active_device_count     SMALLINT            NOT NULL,
    active_alert_count      SMALLINT            NOT NULL,
    recorded_at             TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- Index for historical queries
CREATE INDEX IF NOT EXISTS idx_health_score_log_recorded_at
    ON health_score_log (recorded_at DESC);
