package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AJT — Apache Commons Math K-Means clustering (k=3).
 *
 * Groups all active devices into 3 clusters based on:
 *   [0] average bandwidth %
 *   [1] total MB used in last 7 days
 *
 * Cluster labels are assigned by centroid average bandwidth:
 *   LOW_USAGE   — lightest cluster
 *   MED_USAGE   — middle cluster
 *   HIGH_USAGE  — heaviest cluster
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusteringService {

    private final DeviceRepository   deviceRepository;
    private final UsageLogRepository usageLogRepository;

    private static final int K = 3;

    // Inner class: wraps a device into a point for K-Means
    private static class DevicePoint implements Clusterable {
        final Device device;
        final double[] point;

        DevicePoint(Device device, double avgBandwidth, double totalMB) {
            this.device = device;
            this.point  = new double[]{ avgBandwidth, totalMB };
        }

        @Override
        public double[] getPoint() { return point; }
    }

    /**
     * Runs K-Means and returns a list of device cluster assignments.
     * Each entry: { deviceId, deviceName, cluster, avgBandwidth, totalMB }
     */
    public List<Map<String, Object>> clusterDevices() {
        List<Device> devices = deviceRepository.findByIsActiveTrue();
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        List<DevicePoint> points = devices.stream().map(device -> {
            var logs = usageLogRepository.findByDeviceAndTimestampAfter(device, since);
            double avgBw  = logs.stream().mapToDouble(l -> l.getBandwidthPercentage()).average().orElse(0.0);
            double totalMB = logs.stream().mapToDouble(l -> l.getBytesUsed()).sum();
            return new DevicePoint(device, avgBw, totalMB);
        }).collect(Collectors.toList());

        if (points.size() < K) {
            // Not enough devices to cluster — return everyone in cluster 0
            return points.stream().map(p -> buildEntry(p, "LOW_USAGE")).collect(Collectors.toList());
        }

        KMeansPlusPlusClusterer<DevicePoint> clusterer =
                new KMeansPlusPlusClusterer<>(K, 100);
        List<CentroidCluster<DevicePoint>> clusters = clusterer.cluster(points);

        // Sort clusters by centroid[0] (avg bandwidth) ascending → LOW, MED, HIGH
        clusters.sort(Comparator.comparingDouble(c -> c.getCenter().getPoint()[0]));
        String[] labels = { "LOW_USAGE", "MED_USAGE", "HIGH_USAGE" };

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            String label = labels[Math.min(i, labels.length - 1)];
            for (DevicePoint dp : clusters.get(i).getPoints()) {
                result.add(buildEntry(dp, label));
            }
        }

        log.info("ClusteringService: clustered {} devices into {} groups.", points.size(), K);
        return result;
    }

    private Map<String, Object> buildEntry(DevicePoint dp, String cluster) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId",      dp.device.getId());
        m.put("deviceName",    dp.device.getDeviceName());
        m.put("cluster",       cluster);
        m.put("avgBandwidth",  Math.round(dp.point[0] * 100.0) / 100.0);
        m.put("totalMB",       Math.round(dp.point[1] * 100.0) / 100.0);
        return m;
    }
}
