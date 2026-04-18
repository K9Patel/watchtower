-- ================================================================
--  WatchTower — V6 Device Baseline
--  New table: device_baseline — per-device hourly/weekday average
--  bandwidth and stddev for anomaly detection.
--  Also adds baseline_ready BOOLEAN to device table.
-- ================================================================

-- ── 1. DEVICE_BASELINE ──────────────────────────────────────────
-- Stores rolling 7-day behaviour baselines per device, broken down
-- by hour-of-day (0-23) and day-of-week (1=Mon … 7=Sun).
-- Used by the anomaly-detection engine to compare live bandwidth
-- against the statistical "normal" for that device/time slot.

CREATE TABLE device_baseline (
    id               BIGSERIAL        PRIMARY KEY,
    device_id        BIGINT           NOT NULL
                         REFERENCES device (id) ON DELETE CASCADE,
    hour_of_day      SMALLINT         NOT NULL,   -- 0-23
    day_of_week      SMALLINT         NOT NULL,   -- 1=Mon ... 7=Sun
    avg_bandwidth    DOUBLE PRECISION NOT NULL DEFAULT 0,
    stddev_bandwidth DOUBLE PRECISION NOT NULL DEFAULT 0,
    sample_count     INTEGER          NOT NULL DEFAULT 0,
    last_updated     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_baseline_device_hour_day
        UNIQUE (device_id, hour_of_day, day_of_week),
    CONSTRAINT chk_hour_of_day
        CHECK (hour_of_day BETWEEN 0 AND 23),
    CONSTRAINT chk_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7)
);

-- Fast lookup by device for baseline computation
CREATE INDEX idx_baseline_device ON device_baseline (device_id);


-- ── 2. ALTER DEVICE — add baseline_ready flag ───────────────────
-- FALSE until the baseline engine has accumulated enough samples
-- for meaningful anomaly scoring.

ALTER TABLE device
    ADD COLUMN baseline_ready BOOLEAN NOT NULL DEFAULT FALSE;

-- ── END V6 ──────────────────────────────────────────────────────
