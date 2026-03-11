package com.watchtower.backend.repository;

import com.watchtower.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // SimulatorService: loops all active devices every 10s
    List<Device> findByIsActiveTrue();

    // StatsController: filter by STUDENT / STAFF / ADMIN
    List<Device> findByDeviceType(Device.DeviceType deviceType);

    // RealDataService: find or create "My Laptop" device
    Optional<Device> findByDeviceName(String deviceName);

    // NetworkMonitorService: look up device by IP for ping
    Optional<Device> findByIpAddress(String ipAddress);
}