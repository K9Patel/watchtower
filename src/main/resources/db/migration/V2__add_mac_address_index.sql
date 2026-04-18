-- ================================================================
--  WatchTower — V2 Add MAC address index for NetworkDiscoveryService
--  Supports: findByMacAddress() in real network mode
-- ================================================================

CREATE INDEX IF NOT EXISTS idx_device_mac_address ON device (mac_address);
