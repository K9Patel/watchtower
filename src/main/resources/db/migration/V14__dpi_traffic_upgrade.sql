-- WatchTower V14: DPI traffic compatibility upgrade
--
-- Why this migration exists:
-- 1) Some environments already have V8 dpi_traffic + basic device snapshot columns.
-- 2) This feature needs richer live snapshot fields on device.
-- 3) Flyway versioning in this repo is already at V13, so we use V14.

CREATE TABLE IF NOT EXISTS dpi_traffic (
    id               BIGSERIAL PRIMARY KEY,
    device_id        BIGINT NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    service_name     VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    traffic_category VARCHAR(20) NOT NULL DEFAULT 'BROWSING',
    sni_hostname     VARCHAR(255),
    destination_ip   VARCHAR(45),
    port             INTEGER,
    packets_count    INTEGER NOT NULL DEFAULT 0,
    bytes_captured   BIGINT NOT NULL DEFAULT 0,
    confidence       SMALLINT NOT NULL DEFAULT 50,
    classified_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dpi_category CHECK (traffic_category IN (
        'STREAMING', 'GAMING', 'VOIP', 'DOWNLOAD', 'BROWSING', 'UNKNOWN'
    )),
    CONSTRAINT chk_dpi_confidence CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_dpi_device_time ON dpi_traffic (device_id, classified_at DESC);
CREATE INDEX IF NOT EXISTS idx_dpi_service_time ON dpi_traffic (service_name, classified_at DESC);
CREATE INDEX IF NOT EXISTS idx_dpi_category_time ON dpi_traffic (traffic_category, classified_at DESC);

ALTER TABLE device
    ADD COLUMN IF NOT EXISTS current_service VARCHAR(30) DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS current_category VARCHAR(20) DEFAULT 'BROWSING',
    ADD COLUMN IF NOT EXISTS current_sni_hostname VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_destination_ip VARCHAR(45),
    ADD COLUMN IF NOT EXISTS current_destination_port INTEGER,
    ADD COLUMN IF NOT EXISTS dpi_last_updated TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();
