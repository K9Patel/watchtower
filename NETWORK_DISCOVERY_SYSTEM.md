# Real-Time Network Device Discovery System — WatchTower

## Overview

This system implements a **zero fake data** philosophy for network device discovery:
- **Actual pings** determine online/offline status (not ARP cache age)
- **Real hostnames** from reverse DNS lookups (never generated names)
- **IEEE OUI database** for vendor names (never guessed from partial data)
- **Network change detection** for when moving between WiFi networks

## Architecture

### PART 1: Online/Offline Detection (Real Pings)

```java
private boolean pingDevice(String ipAddress) {
    InetAddress address = InetAddress.getByName(ipAddress);
    return address.isReachable(1500);  // ICMP ping with 1.5s timeout
}
```

**Key principle**: A device is ONLINE only if it responds to ICMP ping RIGHT NOW in this scan cycle.

- If `isReachable()` returns `true` → status = ONLINE
- If `isReachable()` returns `false` or throws exception → status = OFFLINE
- No heuristics, no assumptions, no cache
- Every device pinged during every 30-second scan

### PART 2: Network Change Detection

```java
private String getCurrentNetworkPrefix() {
    // Get first active IPv4 address from network interface
    // Extract first 3 octets: "192.168.1" from "192.168.1.5"
}
```

**Behavior**:
1. Every scan gets current network prefix
2. Compare with `previousNetworkPrefix` stored in memory
3. If prefix changed (e.g., "192.168.1" → "192.168.43"):
   - DELETE all auto-discovered devices immediately
   - Push WebSocket event to `/topic/network-change`
   - Run fresh full scan
   - Update stored prefix

Example: Moving from home WiFi (192.168.1.x) to guest WiFi (192.168.43.x) clears the device table and rescans.

### PART 3: Device Name Resolution (Real Hostnames Only)

```java
private String reverseDnsLookup(String ipAddress) {
    InetAddress addr = InetAddress.getByName(ipAddress);
    String hostname = addr.getCanonicalHostName();
    
    if (hostname.equals(ipAddress)) {
        return null;  // DNS has no record → store null, show IP in frontend
    }
    return hostname;  // Real hostname found
}
```

**Principle**: Never generate device names like "Device-A1B2"

- If reverse DNS returns a real hostname → use it (e.g., "john-macbook.local")
- If DNS returns same as IP address → store null
- Frontend displays null as the IP address
- Device name stored in database, only updated when DNS lookup succeeds

### PART 4: MAC Vendor Lookup (IEEE OUI Database Only)

The `MacVendorService` downloads from https://maclookup.app/downloads/json-database/get-db

```java
public String lookup(String mac) {
    String oui = mac.substring(0, 8).toUpperCase();  // "AA:BB:CC"
    return ouiMap.getOrDefault(oui, null);  // null if not found
}
```

**Behavior**:
- First startup: Downloads full IEEE OUI database (~50K+ entries)
- Cached to `src/main/resources/oui/oui-database.json`
- Subsequent startups: Load from disk (no re-download)
- Lookup: MAC prefix → vendor name, or null if not in database
- Never interpolates, never guesses, never uses partial matches

### PART 5: Full Scan Flow (Every 30 Seconds)

```
Step 1: Detect network change
  → if changed: clear all auto-discovered devices, push WS event

Step 2: Run "arp -a" → parse all IP+MAC pairs
  Skip: broadcast (.255), multicast MACs, ff:ff:ff:ff:ff:ff, incomplete entries
  Normalize MAC to lowercase colon format: aa:bb:cc:dd:ee:ff

Step 3: For each valid IP+MAC pair:
  a. Ping the IP → determine ONLINE or OFFLINE right now
  b. Reverse DNS → get real hostname or null
  c. OUI lookup → get real vendor or null
  d. Find device in DB by MAC address:
       EXISTS → update: ipAddress, status, lastSeenAt, deviceName (if DNS resolved), vendorName (if OUI found)
       NEW    → insert: all fields, isAutoDiscovered=true

Step 4: Find devices in DB NOT in this ARP scan:
  → These devices have left the network
  → If isAutoDiscovered=true → DELETE immediately
  → If isAutoDiscovered=false (manually added) → set status=OFFLINE only

Step 5: Push WebSocket message to /topic/devices with updated list

Step 6: Store ScanResult with:
  - totalFound, newDevices, updatedDevices, removedStale
  - scannedAt timestamp
  - networkPrefix, networkChanged flag
  - full device list
```

## Database Schema

### V1 — Initial Schema (existing)
- id, device_name, ip_address, mac_address, is_active, status, registered_at

### V2 — MAC Address Index (existing)
- CREATE INDEX idx_device_mac_address

### V3 — Last Seen Timestamp (existing)
- last_seen_at (updated every scan to track online devices)

### V4 — Remove DeviceType (existing)
- Dropped device_type column

### V5 — Auto-Discovery Fields (existing)
- is_auto_discovered (TRUE → auto-pruned after 5 min absence)
- vendor_name (from OUI lookup)
- os_type (reserved for future OS fingerprinting)
- Indexes: idx_device_last_seen_at, idx_device_auto_discovered

### V6 — Nullable Device Name (NEW)
- device_name: NULL allowed (for DNS-less devices)

## API Endpoints

### GET /api/devices
Returns all devices in database (existing endpoint, unchanged)

### POST /api/devices/scan
Trigger immediate manual scan in background thread

Response:
```json
{
  "message": "Network scan started. Poll GET /api/devices/scan/status for results.",
  "scanning": true,
  "startedAt": "2026-04-18T10:30:45"
}
```

### GET /api/devices/scan/status
Returns last scan result with network info

Response:
```json
{
  "scanning": false,
  "totalInDatabase": 12,
  "onlineNow": 8,
  "lastScan": {
    "scannedAt": "2026-04-18T10:30:45",
    "totalFound": 8,
    "newDevices": 2,
    "updatedDevices": 1,
    "removedStale": 0,
    "networkPrefix": "192.168.1",
    "networkChanged": false
  },
  "devices": [
    {
      "id": 1,
      "deviceName": "john-macbook.local",
      "ipAddress": "192.168.1.10",
      "macAddress": "aa:bb:cc:dd:ee:ff",
      "status": "ONLINE",
      "vendorName": "Apple, Inc.",
      "isAutoDiscovered": true,
      "lastSeenAt": "2026-04-18T10:30:45"
    },
    {
      "id": 2,
      "deviceName": null,
      "ipAddress": "192.168.1.20",
      "macAddress": "11:22:33:44:55:66",
      "status": "ONLINE",
      "vendorName": "Samsung Electronics",
      "isAutoDiscovered": true,
      "lastSeenAt": "2026-04-18T10:30:45"
    }
  ]
}
```

## WebSocket Events

### /topic/devices
Pushed when a device is discovered, updated, or status changes

```json
{
  "id": 1,
  "deviceName": "john-macbook.local",
  "ipAddress": "192.168.1.10",
  "status": "ONLINE",
  "vendorName": "Apple, Inc.",
  "lastSeenAt": "2026-04-18T10:30:45"
}
```

### /topic/network-change
Pushed when network prefix changes

```json
{
  "type": "NETWORK_CHANGED",
  "previousNetwork": "192.168.1",
  "currentNetwork": "192.168.43",
  "changedAt": "2026-04-18T10:35:00",
  "message": "Network changed — rescanning devices"
}
```

## Implementation Notes

### Key Files Modified
1. **NetworkDiscoveryService.java** — Core discovery logic with ping + DNS
2. **Device.java** — deviceName now nullable
3. **DeviceRepository.java** — Added findByIsAutoDiscoveredTrue()
4. **DashboardWebSocketService.java** — Added pushMessage() method
5. **DeviceController.java** — Updated scan/status endpoint
6. **V6__make_device_name_nullable.sql** — Migration for nullable device_name

### Key Files Unchanged
1. **MacVendorService.java** — Already implemented correctly
2. **V1-V5 Migrations** — All working as intended

### Design Decisions

#### Why null for device names without DNS records?
- Frontend can display null as the IP address
- Clearly indicates "we tried to resolve this but DNS had no record"
- Never shows fake generated names like "Device-EEFF"
- Matches user's "zero fake data" philosophy

#### Why 1.5 second ping timeout?
- Default InetAddress.isReachable() is 0ms (returns immediately)
- 1.5 seconds balances: detection accuracy vs scan speed
- On 254-host LAN: ~4 minutes for full ping scan
- Acceptable trade-off for accuracy

#### Why 30 second scan frequency?
- Every 60 seconds in original, changed to 30 for real-time responsiveness
- Stale device timeout: 5 minutes (10 missed scans)
- Manual trigger available anytime via POST /api/devices/scan

#### Why delete auto-discovered devices when network changes?
- Device list should reflect current network, not historical devices
- Prevents clutter from previous networks
- Manually added devices (isAutoDiscovered=false) kept as OFFLINE

## Testing Checklist

- [ ] Start application, verify MacVendorService downloads OUI database
- [ ] Connected to WiFi, run POST /api/devices/scan
- [ ] Verify devices appear with real hostnames (from DNS) or IP addresses (null hostnames)
- [ ] Verify vendor names populated from IEEE OUI database
- [ ] Move to different WiFi network, run scan again
- [ ] Verify network-change event pushed and old devices deleted
- [ ] Unplug device from network
- [ ] Wait 5 minutes, verify device auto-deleted if auto-discovered
- [ ] Manually add a device via POST /api/devices
- [ ] After 5 min absence, verify it's marked OFFLINE not deleted
- [ ] Ping a device that's offline (turn off WiFi)
- [ ] Verify status changes to OFFLINE within 30 seconds
- [ ] Ping a device that's online, verify status ONLINE
- [ ] Check WebSocket messages flowing to /topic/devices and /topic/network-change

## Philosophy Summary

> **No guessing. No fake data. Only truth.**

- Online/Offline: Actual ping response, not assumptions
- Device Names: Real hostnames from DNS, or null if unknown
- Vendor Names: IEEE OUI database, or null if not found
- Network Changes: Detected and handled automatically
- Device Lifecycle: Tracked by MAC address, auto-pruned after absence

This system provides accurate, real-time visibility into the actual state of the network, RIGHT NOW.
