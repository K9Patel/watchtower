-- ================================================================
--  WatchTower — V1 Initial Schema
--  Database  : PostgreSQL 15+
--  Location  : src/main/resources/db/migration/V1__init_watchtower_schema.sql
--  Run by    : Flyway automatically on first startup
-- ================================================================


-- ── 1. DEVICE ────────────────────────────────────────────────
-- Every UsageLog and Alert has a foreign key to this table.
-- DeviceType: STUDENT | STAFF | ADMIN
-- Status:     ONLINE  | OFFLINE  (updated by NetworkMonitorService)

CREATE TABLE IF NOT EXISTS device (
    id              BIGSERIAL                   PRIMARY KEY,
    device_name     VARCHAR(100)                NOT NULL,
    ip_address      VARCHAR(45)                 NOT NULL,
    mac_address     VARCHAR(17),
    device_type     VARCHAR(20)                 NOT NULL
                        CHECK (device_type IN ('STUDENT', 'STAFF', 'ADMIN')),
    is_active       BOOLEAN                     NOT NULL DEFAULT TRUE,
    status          VARCHAR(10)                 NOT NULL DEFAULT 'ONLINE'
                        CHECK (status IN ('ONLINE', 'OFFLINE')),
    registered_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- SimulatorService queries findByIsActiveTrue() every 10s
CREATE INDEX IF NOT EXISTS idx_device_is_active   ON device (is_active);
CREATE INDEX IF NOT EXISTS idx_device_type        ON device (device_type);
CREATE INDEX IF NOT EXISTS idx_device_ip_address  ON device (ip_address);


-- ── 2. USAGE_LOG ─────────────────────────────────────────────
-- Written every 10s by SimulatorService (profile=simulator)
--                   OR RealDataService  (profile=real)
-- Read by: AnalysisService, DiagnosisEngine, HistoryAnalysisService
-- TrafficType: STREAMING | BROWSING | GAMING | DOWNLOAD | VOIP | REAL_TRAFFIC

CREATE TABLE IF NOT EXISTS usage_log (
    id                      BIGSERIAL                   PRIMARY KEY,
    device_id               BIGINT                      NOT NULL
                                REFERENCES device (id) ON DELETE CASCADE,
    bytes_used              DOUBLE PRECISION            NOT NULL DEFAULT 0,
    bandwidth_percentage    DOUBLE PRECISION            NOT NULL DEFAULT 0
                                CHECK (bandwidth_percentage >= 0 AND bandwidth_percentage <= 100),
    traffic_type            VARCHAR(20)                 NOT NULL DEFAULT 'BROWSING'
                                CHECK (traffic_type IN (
                                    'STREAMING', 'BROWSING', 'GAMING',
                                    'DOWNLOAD', 'VOIP', 'REAL_TRAFFIC'
                                )),
    timestamp               TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- DiagnosisEngine: findByTimestampAfter(now - 60s) runs every 30s
-- HistoryAnalysisService: GROUP BY DATE_TRUNC('hour', timestamp)
CREATE INDEX IF NOT EXISTS idx_usage_log_timestamp        ON usage_log (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_usage_log_device_id        ON usage_log (device_id);
CREATE INDEX IF NOT EXISTS idx_usage_log_device_ts        ON usage_log (device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_usage_log_traffic_type     ON usage_log (traffic_type);


-- ── 3. ALERT ─────────────────────────────────────────────────
-- Created by DiagnosisEngine when a Strategy rule fires.
-- Observer pattern: AlertPublisher → DatabaseAlertListener saves here.
-- Severity: LOW | MEDIUM | HIGH | CRITICAL
-- AlertType examples: ANOMALY | CONGESTION | SPIKE | STREAMING_OVERLOAD

CREATE TABLE IF NOT EXISTS alert (
    id              BIGSERIAL                   PRIMARY KEY,
    device_id       BIGINT                      NOT NULL
                        REFERENCES device (id) ON DELETE CASCADE,
    alert_type      VARCHAR(50)                 NOT NULL,
    severity        VARCHAR(10)                 NOT NULL
                        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    message         TEXT                        NOT NULL,
    is_resolved     BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- AlertController.getActiveAlerts() — dashboard polls every 10s
-- Sorted CRITICAL first, then by created_at DESC
CREATE INDEX IF NOT EXISTS idx_alert_is_resolved  ON alert (is_resolved);
CREATE INDEX IF NOT EXISTS idx_alert_severity     ON alert (severity);
CREATE INDEX IF NOT EXISTS idx_alert_device_id    ON alert (device_id);
CREATE INDEX IF NOT EXISTS idx_alert_created_at   ON alert (created_at DESC);


-- ── 4. ADMIN_USER ────────────────────────────────────────────
-- Spring Security user store.
-- Passwords are BCrypt hashed — NEVER plain text in this column.
-- Role: ADMIN → full access | USER → own device stats only

CREATE TABLE IF NOT EXISTS admin_user (
    id          BIGSERIAL       PRIMARY KEY,
    username    VARCHAR(50)     NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    email       VARCHAR(150)    NOT NULL UNIQUE,
    role        VARCHAR(10)     NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('ADMIN', 'USER'))
);

-- Spring Security loads user by username on every login attempt
CREATE INDEX IF NOT EXISTS idx_admin_user_username ON admin_user (username);


-- ── 5. SEED: DEFAULT ADMIN ACCOUNT ───────────────────────────
-- Username : admin
-- Password : admin123  (BCrypt hash, cost=10)
-- Change this password immediately after first login in production.

INSERT INTO admin_user (username, password, email, role)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lvcW',
    'knpatel9965@gmail.com',
    'ADMIN'
)
ON CONFLICT (username) DO NOTHING;

-- ── END V1 ───────────────────────────────────────────────────