-- ================================================================
--  WatchTower — V5 Live Network Auto-Discovery Fields
--  Prerequisite columns already in place:
--    last_seen_at  → added by V3
--    mac_address   → added by V1 (index added by V2)
-- ================================================================

-- Mark whether a device was auto-discovered via ARP scan (vs manually added)
-- Auto-discovered devices are pruned after 5 minutes absence from the network
ALTER TABLE device
    ADD COLUMN IF NOT EXISTS is_auto_discovered BOOLEAN NOT NULL DEFAULT FALSE;

-- MAC vendor name — populated by MacVendorService OUI lookup (Feature 3)
ALTER TABLE device
    ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(100);

-- OS fingerprint — populated by fingerprinting service (Feature 6)
ALTER TABLE device
    ADD COLUMN IF NOT EXISTS os_type VARCHAR(50);

-- Fast lookup for stale device cleanup (DELETE WHERE lastSeenAt < cutoff)
CREATE INDEX IF NOT EXISTS idx_device_last_seen_at
    ON device (last_seen_at);

-- Fast filter: "find all auto-discovered devices older than X"
CREATE INDEX IF NOT EXISTS idx_device_auto_discovered
    ON device (is_auto_discovered, last_seen_at);
