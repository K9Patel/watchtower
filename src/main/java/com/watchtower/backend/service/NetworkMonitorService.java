package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkMonitorService {

    private final DeviceRepository deviceRepository;
    private final UsageLogRepository usageLogRepository;

    // AJT Unit 3 — Java Networking:
    // InetAddress.isReachable() sends a ping to each device IP.
    // Updates device.status to ONLINE or OFFLINE in the database.
    // Runs every 30 seconds in a background thread.
    @Scheduled(fixedRate = 30000)
    public void updateAllDeviceStatuses() {
        List<Device> devices = deviceRepository.findAll();
        int online = 0, offline = 0;

        for (Device device : devices) {
            try {
                // Device may have been deleted/updated by another scheduler cycle.
                var fresh = deviceRepository.findById(device.getId());
                if (fresh.isEmpty()) {
                    continue;
                }
                Device current = fresh.get();

                // AJT Unit 3: InetAddress.getByName() resolves IP string to InetAddress object
                InetAddress addr = InetAddress.getByName(current.getIpAddress());

                // isReachable(2000) = ping with 2 second timeout
                boolean reachable = addr.isReachable(2000);

                String newStatus = reachable ? "ONLINE" : "OFFLINE";

                // only save if status actually changed — avoid unnecessary DB writes
                if (!newStatus.equals(current.getStatus())) {
                    current.setStatus(newStatus);
                    deviceRepository.save(current);
                    log.info("Device {} status changed to {}", current.getDeviceName(), newStatus);
                }

                if (reachable)
                    online++;
                else
                    offline++;

            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.debug("NetworkMonitor: skipped stale device row {}", device.getId());
                offline++;
            } catch (Exception e) {
                try {
                    deviceRepository.findById(device.getId()).ifPresent(d -> {
                        d.setStatus("OFFLINE");
                        deviceRepository.save(d);
                    });
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ignored) {
                    log.debug("NetworkMonitor: concurrent update while marking OFFLINE for {}", device.getId());
                }
                offline++;
            }
        }

        log.debug("NetworkMonitor: {} online, {} offline", online, offline);
    }

    // AJT Unit 3 — Java Networking:
    // Calculates live Mbps for a single device from last 60 seconds of logs.
    // Used by StatsController to show per-device live bandwidth.
    public double getCurrentMbps(Device device) {
        List<UsageLog> recent = usageLogRepository.findByDeviceAndTimestampAfter(
                device, LocalDateTime.now().minusSeconds(60));

        // Java Streams + Lambdas — sum all bytesUsed in last 60s, divide by 60
        double totalMB = recent.stream()
                .mapToDouble(UsageLog::getBytesUsed)
                .sum();

        return totalMB / 60.0;
    }

    // Returns live status snapshot for all devices
    public List<Device> getAllDevicesWithStatus() {
        return deviceRepository.findAll();
    }
}