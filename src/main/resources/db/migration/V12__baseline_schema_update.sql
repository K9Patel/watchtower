-- ================================================================
--  WatchTower — V12 Baseline Schema Update
--  Reconciles baseline schema safely across mixed database states.
--  This migration must work whether `device_baseline` already exists or not.
-- ================================================================

-- ── 1. Ensure baseline table exists in current shape ──────────────
CREATE TABLE IF NOT EXISTS device_baseline (
    id               BIGSERIAL        PRIMARY KEY,
    device_id        BIGINT           NOT NULL
                         REFERENCES device (id) ON DELETE CASCADE,
    hour_of_day      SMALLINT         NOT NULL,
    avg_bandwidth    DOUBLE PRECISION NOT NULL DEFAULT 0,
    stddev_bandwidth DOUBLE PRECISION NOT NULL DEFAULT 0,
    sample_count     INTEGER          NOT NULL DEFAULT 0,
    last_updated     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_baseline_device ON device_baseline (device_id);

-- ── 2. If legacy day_of_week schema exists, normalize it ──────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'device_baseline'
          AND column_name = 'day_of_week'
    ) THEN
        -- Keep latest row per (device_id, hour_of_day) before enforcing new uniqueness.
        DELETE FROM device_baseline d
        USING device_baseline k
        WHERE d.device_id = k.device_id
          AND d.hour_of_day = k.hour_of_day
          AND (
                d.last_updated < k.last_updated
                OR (d.last_updated = k.last_updated AND d.id < k.id)
              );

        ALTER TABLE device_baseline
            DROP CONSTRAINT IF EXISTS uq_baseline_device_hour_day;

        ALTER TABLE device_baseline
            DROP CONSTRAINT IF EXISTS chk_day_of_week;

        ALTER TABLE device_baseline
            DROP COLUMN IF EXISTS day_of_week;
    END IF;
END $$;

-- ── 3. Enforce unique key for 1-day model ─────────────────────────
ALTER TABLE device_baseline
    DROP CONSTRAINT IF EXISTS uq_baseline_device_hour;

ALTER TABLE device_baseline
    ADD CONSTRAINT uq_baseline_device_hour
    UNIQUE (device_id, hour_of_day);

-- ── 4. Ensure device flags exist ──────────────────────────────────
ALTER TABLE device
    ADD COLUMN IF NOT EXISTS baseline_ready BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE device
    ADD COLUMN IF NOT EXISTS baseline_since TIMESTAMP WITHOUT TIME ZONE;

-- ── END V12 ──────────────────────────────────────────────────────
