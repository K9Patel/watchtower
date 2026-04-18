-- ================================================================
--  WatchTower — V8 DPI (Deep Packet Inspection) Summary
--  New table: dpi_traffic — per-device packet classification log.
--  Also adds DPI summary columns to device table.
-- ================================================================

-- ── 1. DPI_TRAFFIC ──────────────────────────────────────────────
-- Each row represents a classified traffic flow for a device.
-- Populated by the DPI engine after SNI / port / heuristic analysis.

CREATE TABLE dpi_traffic (
    id               BIGSERIAL    PRIMARY KEY,
    device_id        BIGINT       NOT NULL
                         REFERENCES device (id) ON DELETE CASCADE,
    service_name     VARCHAR(30)  NOT NULL DEFAULT 'UNKNOWN',
    traffic_category VARCHAR(20)  NOT NULL DEFAULT 'BROWSING',
    sni_hostname     VARCHAR(255),
    destination_ip   VARCHAR(45),
    port             INTEGER,
    packets_count    INTEGER      NOT NULL DEFAULT 0,
    bytes_captured   BIGINT       NOT NULL DEFAULT 0,
    confidence       SMALLINT     NOT NULL DEFAULT 50,
    classified_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dpi_category
        CHECK (traffic_category IN (
            'STREAMING', 'GAMING', 'VOIP',
            'DOWNLOAD', 'BROWSING', 'UNKNOWN'
        )),
    CONSTRAINT chk_dpi_confidence
        CHECK (confidence BETWEEN 0 AND 100)
);

-- Primary query path: "last N classifications for device X"
CREATE INDEX idx_dpi_device_time ON dpi_traffic (device_id, classified_at DESC);


-- ── 2. ALTER DEVICE — add DPI summary columns ──────────────────
-- Quick-access snapshot of what a device is currently doing,
-- updated by the DPI engine after each classification pass.

ALTER TABLE device
    ADD COLUMN current_service  VARCHAR(30) DEFAULT 'UNKNOWN',
    ADD COLUMN current_category VARCHAR(20) DEFAULT 'BROWSING',
    ADD COLUMN dpi_last_updated TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();

-- ── END V8 ──────────────────────────────────────────────────────
