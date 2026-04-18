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

    // SimulatorService / RealDataService: loops all active devices every 10s
    List<Device> findByIsActiveTrue();

    // Removed findByDeviceType
    // RealDataService: find or create "My Laptop" device
    Optional<Device> findByDeviceName(String deviceName);

    // NetworkMonitorService: look up device by IP for ping
    Optional<Device> findByIpAddress(String ipAddress);

    // NetworkDiscoveryService: look up device by MAC address to avoid duplicates
    Optional<Device> findByMacAddress(String macAddress);

    // Find all devices NOT seen since a given time — used to mark them OFFLINE
    // after a scan completes (they weren't in the ARP table → they left the network)
    List<Device> findByLastSeenAtBefore(LocalDateTime cutoff);

    // Bulk-mark all devices OFFLINE on startup so the scan rebuilds truth from zero
    @Modifying
    @Transactional
    @Query("UPDATE Device d SET d.status = 'OFFLINE' WHERE d.deviceName != 'My Laptop'")
    void markAllOffline();

    // Delete devices that have been OFFLINE for longer than a given time
    // (they have permanently left the network — e.g. different WiFi network)
    @Modifying
    @Transactional
    @Query("DELETE FROM Device d WHERE d.status = 'OFFLINE' AND d.lastSeenAt < :cutoff AND d.deviceName != 'My Laptop'")
    void deleteStaleOfflineDevices(@Param("cutoff") LocalDateTime cutoff);
}