-- ================================================================
--  WatchTower — V11 Allow null device names for DNS-less devices
--  
--  Motivation: When reverse DNS lookup fails for auto-discovered 
--  devices, we store null instead of generated names like "Device-A1B2".
--  Frontend displays the IP address in this case.
-- ================================================================

-- Make device_name nullable to support DNS resolution failures
ALTER TABLE device
    ALTER COLUMN device_name DROP NOT NULL;
