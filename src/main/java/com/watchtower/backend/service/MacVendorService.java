package com.watchtower.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MAC Vendor Lookup Service — IEEE OUI Database
 *
 * Resolves a MAC address prefix (OUI — Organizationally Unique Identifier)
 * to a human-readable vendor name using the full IEEE OUI database
 * (~50,000+ entries) downloaded from maclookup.app.
 *
 * On first startup:
 *   1. Checks if src/main/resources/oui/oui-database.json exists
 *   2. If not, downloads it from https://maclookup.app/downloads/json-database/get-db
 *   3. Parses the JSON array into a HashMap<String, String> (OUI prefix → vendor name)
 *   4. All lookups are in-memory — zero latency, zero external API calls per request
 *
 * JSON format: [{"macPrefix":"AA:BB:CC","vendorName":"Vendor Corp","private":false,...}, ...]
 */
@Slf4j
@Service
public class MacVendorService {

    private static final String OUI_DB_URL = "https://maclookup.app/downloads/json-database/get-db";
    private static final String OUI_DB_PATH = "src/main/resources/oui/oui-database.json";

    /** OUI prefix (uppercase, colon-separated, 8 chars like "AA:BB:CC") → vendor name */
    private final Map<String, String> ouiMap = new HashMap<>(65536);

    @PostConstruct
    public void init() {
        try {
            Path dbPath = Path.of(OUI_DB_PATH);

            // Download if not present
            if (!Files.exists(dbPath)) {
                downloadOuiDatabase(dbPath);
            }

            // Load into memory
            loadOuiDatabase(dbPath);

            log.info("MacVendorService: loaded {} OUI entries from IEEE database.", ouiMap.size());

        } catch (Exception e) {
            log.error("MacVendorService: Failed to load OUI database — falling back to empty. Error: {}", e.getMessage());
            loadFallbackTable();
        }
    }

    /**
     * Downloads the IEEE OUI JSON database from maclookup.app.
     * Creates parent directories if needed.
     */
    private void downloadOuiDatabase(Path target) throws Exception {
        log.info("MacVendorService: OUI database not found at {}. Downloading from {}...", target, OUI_DB_URL);

        Files.createDirectories(target.getParent());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OUI_DB_URL))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "WatchTower/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            Files.copy(response.body(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("MacVendorService: Downloaded OUI database to {}", target);
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + " downloading OUI database");
        }
    }

    /**
     * Parses the JSON file into the in-memory HashMap.
     * Expected format: [{"macPrefix":"AA:BB:CC","vendorName":"Vendor Corp",...}, ...]
     */
    private void loadOuiDatabase(Path dbPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File file = dbPath.toFile();

        List<Map<String, Object>> entries = mapper.readValue(file, new TypeReference<>() {});

        for (Map<String, Object> entry : entries) {
            String prefix = (String) entry.get("macPrefix");
            String vendor = (String) entry.get("vendorName");

            if (prefix != null && vendor != null && !vendor.isBlank()) {
                // Normalise to uppercase colon-separated: "AA:BB:CC"
                String normalised = prefix.toUpperCase().replace("-", ":");
                ouiMap.put(normalised, vendor);
            }
        }
    }

    /**
     * Minimal fallback if download/parse fails — covers the most common consumer vendors.
     */
    private void loadFallbackTable() {
        log.warn("MacVendorService: Using minimal fallback OUI table.");
        // Apple
        for (String p : new String[]{"04:15:52","3C:22:FB","A4:83:E7","F0:18:98","DC:A4:CA","78:7B:8A"})
            ouiMap.put(p, "Apple, Inc.");
        // Samsung
        for (String p : new String[]{"08:08:C2","34:14:5F","50:01:BB","78:52:1A","A0:07:98","C4:42:02"})
            ouiMap.put(p, "Samsung Electronics");
        // Intel
        for (String p : new String[]{"00:1E:64","3C:97:0E","68:05:CA","80:86:F2","B4:6B:FC","DC:53:60"})
            ouiMap.put(p, "Intel Corporate");
    }

    /**
     * Resolves a MAC address to its vendor name using the IEEE OUI database.
     *
     * @param mac MAC address in any common format (AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF)
     * @return Vendor name string, or "Unknown" if not found
     */
    public String lookup(String mac) {
        if (mac == null || mac.isBlank()) return "Unknown";

        // Normalise to uppercase colon-separated
        String normalised = mac.toUpperCase().replace("-", ":");

        // Extract OUI prefix (first 3 octets = 8 chars: "AA:BB:CC")
        if (normalised.length() < 8) return "Unknown";
        String oui = normalised.substring(0, 8);

        String vendor = ouiMap.get(oui);
        if (vendor != null) {
            log.debug("MacVendor: {} → {}", oui, vendor);
            return vendor;
        }

        return "Unknown";
    }

    /**
     * Returns a short icon label for the vendor for use in UI device naming.
     * e.g. "Apple, Inc." → "Apple", "Samsung Electronics Co.,Ltd." → "Samsung"
     */
    public String shortLabel(String vendor) {
        if (vendor == null || vendor.equals("Unknown")) return "Unknown";
        if (vendor.startsWith("Apple"))       return "Apple";
        if (vendor.startsWith("Samsung"))     return "Samsung";
        if (vendor.startsWith("Intel"))       return "Intel";
        if (vendor.startsWith("Qualcomm"))    return "Qualcomm";
        if (vendor.startsWith("Google"))      return "Google";
        if (vendor.startsWith("Huawei"))      return "Huawei";
        if (vendor.startsWith("Xiaomi"))      return "Xiaomi";
        if (vendor.startsWith("OnePlus"))     return "OnePlus";
        if (vendor.startsWith("OPPO"))        return "OPPO";
        if (vendor.startsWith("Raspberry"))   return "Raspberry Pi";
        if (vendor.startsWith("TP-Link") || vendor.startsWith("TP-LINK")) return "TP-Link";
        if (vendor.startsWith("Cisco"))       return "Cisco";
        if (vendor.startsWith("Dell"))        return "Dell";
        if (vendor.startsWith("Lenovo"))      return "Lenovo";
        if (vendor.startsWith("HP") || vendor.startsWith("Hewlett")) return "HP";
        if (vendor.startsWith("Amazon"))      return "Amazon";
        if (vendor.startsWith("Sony"))        return "Sony";
        if (vendor.startsWith("Microsoft"))   return "Microsoft";
        if (vendor.startsWith("Netgear") || vendor.startsWith("NETGEAR")) return "Netgear";
        if (vendor.startsWith("ASUSTeK") || vendor.startsWith("ASUS")) return "ASUS";
        if (vendor.startsWith("Realtek"))     return "Realtek";
        if (vendor.startsWith("Broadcom"))    return "Broadcom";
        if (vendor.startsWith("D-Link") || vendor.startsWith("D-LINK")) return "D-Link";
        if (vendor.startsWith("LG"))          return "LG";
        if (vendor.startsWith("Nokia"))       return "Nokia";
        if (vendor.startsWith("Motorola"))    return "Motorola";
        // Return first word for any other vendor
        return vendor.split("[ ,./]")[0];
    }
    
    /**
     * Returns the total number of loaded OUI entries (for diagnostics).
     */
    public int getDatabaseSize() {
        return ouiMap.size();
    }
}
