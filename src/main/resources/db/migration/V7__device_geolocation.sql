-- ================================================================
--  WatchTower — V7 Device Geolocation
--  New table: device_geolocation — IP-to-location data per device.
--  One row per device (UNIQUE on device_id).
-- ================================================================

-- ── 1. DEVICE_GEOLOCATION ───────────────────────────────────────
-- Populated by GeoLocationService on device registration or IP change.
-- Source can be IP_API (free tier), MAXMIND (GeoIP2 DB), or MANUAL.

CREATE TABLE device_geolocation (
    id           BIGSERIAL        PRIMARY KEY,
    device_id    BIGINT           NOT NULL UNIQUE
                     REFERENCES device (id) ON DELETE CASCADE,
    ip_address   VARCHAR(45)      NOT NULL,
    country_code VARCHAR(5),
    country_name VARCHAR(100),
    region_name  VARCHAR(100),
    city_name    VARCHAR(100),
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    isp_name     VARCHAR(255),
    timezone     VARCHAR(60),
    is_private   BOOLEAN          NOT NULL DEFAULT FALSE,
    source       VARCHAR(20)      NOT NULL DEFAULT 'IP_API',
    last_updated TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_geo_source
        CHECK (source IN ('IP_API', 'MAXMIND', 'MANUAL'))
);

-- Fast lookup by device_id (covered by UNIQUE, but explicit for clarity)
CREATE INDEX idx_geo_device_id ON device_geolocation (device_id);

-- ── END V7 ──────────────────────────────────────────────────────
