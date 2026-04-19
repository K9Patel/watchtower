package com.watchtower.backend.repository;

import com.watchtower.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // ── Existing queries (unchanged) ─────────────────────────────────────────

    /** Dashboard table & Overview page */
    List<Device> findByIsActiveTrue();

    /** RealDataService: find-or-create "My Laptop" entry */
    Optional<Device> findByDeviceName(String deviceName);

    /** NetworkMonitorService: look up device by IP */
    Optional<Device> findByIpAddress(String ipAddress);

    /** Find all devices with a given IP address (for DHCP IP reassignment cleanup) */
    List<Device> findAllByIpAddress(String ipAddress);

    // ── V5: auto-discovery queries ───────────────────────────────────────────

    /**
     * Primary discovery lookup — MAC address is the stable identifier
     * for a physical device even when its IP changes via DHCP.
     */
    Optional<Device> findByMacAddress(String macAddress);

    /**
     * Stale device cleanup — returns auto-discovered devices that were NOT
     * seen in the ARP table within the last `cutoff` duration.
     * These are deleted before each scan to keep the table as a live snapshot.
     */
    List<Device> findByIsAutoDiscoveredTrueAndLastSeenAtBefore(LocalDateTime cutoff);

    /**
     * Returns all auto-discovered devices (for network change detection).
     * When the network changes, all auto-discovered devices are deleted.
     */
    List<Device> findByIsAutoDiscoveredTrue();

    /** Bulk-mark all non-manual devices OFFLINE at startup */
    @Modifying
    @Transactional
    @Query("UPDATE Device d SET d.status = 'OFFLINE' WHERE d.isAutoDiscovered = true")
    void markAllAutoDiscoveredOffline();

        /**
         * Updates live DPI snapshot columns used by DevicesTable and DeviceDetail page.
         */
        @Modifying
        @Transactional
        @Query("UPDATE Device d SET " +
            "d.currentService = :service, " +
            "d.currentCategory = :category, " +
            "d.currentSniHostname = :sniHostname, " +
            "d.currentDestinationIp = :destinationIp, " +
            "d.currentDestinationPort = :destinationPort, " +
            "d.dpiLastUpdated = :updatedAt " +
            "WHERE d.id = :deviceId")
        int updateDpiSnapshot(
            @Param("deviceId") Long deviceId,
            @Param("service") String service,
            @Param("category") String category,
            @Param("sniHostname") String sniHostname,
            @Param("destinationIp") String destinationIp,
            @Param("destinationPort") Integer destinationPort,
            @Param("updatedAt") LocalDateTime updatedAt
        );

    /** Hard-delete stale auto-discovered devices (left the network) */
    @Modifying
    @Transactional
    @Query("DELETE FROM Device d WHERE d.isAutoDiscovered = true AND d.lastSeenAt < :cutoff")
    int deleteStaleAutoDiscovered(@Param("cutoff") LocalDateTime cutoff);
}