package com.watchtower.backend.service;

import com.sun.jna.NativeLibrary;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DpiTraffic;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.DpiTrafficRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DpiDataService {

    private static final int SNAPLEN = 65_536;
    private static final int READ_TIMEOUT_MS = 10;

    private final DeviceRepository deviceRepository;
    private final DpiTrafficRepository dpiTrafficRepository;
    private final DashboardWebSocketService dashboardWebSocketService;

    private final DpiClassifierService classifier = new DpiClassifierService();
    private final Map<String, Long> recentFlows = new ConcurrentHashMap<>();

    @Value("${watchtower.dpi.enabled:${app.dpi.enabled:false}}")
    private boolean dpiEnabled;

    @Value("${watchtower.dpi.interface-hint:${watchtower.dpi.interface:${app.dpi.interface:}}}")
    private String interfaceHint;

    @Value("${watchtower.dpi.capture-filter:${app.dpi.capture-filter:tcp dst port 443}}")
    private String captureFilter;

    @Value("${watchtower.dpi.min.confidence:${watchtower.dpi.min-confidence:${app.dpi.min.confidence:${app.dpi.min-confidence:0}}}}")
    private int minConfidence;

    @Value("${watchtower.pcap.windows-dll-dir:}")
    private String configuredWindowsDllDir;

    private volatile boolean running = false;
    private volatile String localIp;
    private volatile String localPrefix;
    private volatile PcapHandle handle;
    private volatile Thread captureThread;

    @PostConstruct
    public void startIfEnabled() {
        if (!dpiEnabled) {
            log.info("DPI: disabled (watchtower.dpi.enabled=false)");
            return;
        }
        log.info("DPI: enabled (interfaceHint='{}', filter='{}', minConfidence={})",
                interfaceHint,
                effectiveCaptureFilter(),
                effectiveMinConfidence());
        startCapture();
    }

    @PreDestroy
    public void stopCapture() {
        running = false;
        try {
            if (handle != null && handle.isOpen()) {
                try {
                    handle.breakLoop();
                } catch (NotOpenException ignored) {
                    // no-op
                }
                handle.close();
            }
        } catch (Exception ex) {
            log.debug("DPI: capture shutdown warning: {}", ex.getMessage());
        }
    }

    private void startCapture() {
        try {
            prepareNativeCaptureLibraryOnWindows();

            localIp = resolveLocalIpv4();
            localPrefix = localIp == null ? null : first3Octets(localIp);

            Optional<PcapNetworkInterface> maybeInterface = findCaptureInterface();
            if (maybeInterface.isEmpty()) {
                log.warn("DPI: no capture interface found. Install Npcap and run as administrator in Windows.");
                return;
            }

            PcapNetworkInterface nif = maybeInterface.get();
            handle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, READ_TIMEOUT_MS);
            handle.setFilter(effectiveCaptureFilter(), BpfProgram.BpfCompileMode.OPTIMIZE);

            PacketListener listener = this::onPacket;
            captureThread = new Thread(() -> {
                running = true;
                try {
                    log.info("DPI: Packet capture active on {}", readableName(nif));
                    handle.loop(-1, listener);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (NotOpenException ex) {
                    log.warn("DPI: capture handle closed.");
                } catch (Exception ex) {
                    log.warn("DPI: capture stopped due to error: {}", ex.getMessage());
                } finally {
                    running = false;
                }
            }, "dpi-packet-capture");

            captureThread.setDaemon(true);
            captureThread.start();
        } catch (Throwable ex) {
            running = false;
            log.warn("DPI: unavailable ({}).", ex.getMessage());
        }
    }

    private void onPacket(Packet packet) {
        if (!running) {
            return;
        }

        try {
            IpV4Packet ip = packet.get(IpV4Packet.class);
            TcpPacket tcp = packet.get(TcpPacket.class);
            if (ip == null || tcp == null) {
                return;
            }

            String srcIp = ip.getHeader().getSrcAddr().getHostAddress();
            String dstIp = ip.getHeader().getDstAddr().getHostAddress();
            int dstPort = tcp.getHeader().getDstPort().valueAsInt();

            // We classify outbound TLS connections initiated by local devices.
            if (!isLocalSubnetAddress(srcIp) || isLocalSubnetAddress(dstIp)) {
                return;
            }

            Packet tcpPayload = tcp.getPayload();
            if (tcpPayload == null) {
                return;
            }

            byte[] payload = tcpPayload.getRawData();
            if (payload == null || payload.length < 5) {
                return;
            }

            String sni = extractSni(payload);
            if (sni == null) {
                return;
            }

            if (isRecentDuplicate(srcIp, dstIp, dstPort, sni)) {
                return;
            }

            Optional<Device> deviceOpt = deviceRepository.findByIpAddress(srcIp);
            if (deviceOpt.isEmpty()) {
                return;
            }

            persistClassification(deviceOpt.get(), sni, dstIp, dstPort, ip.getHeader().getTotalLengthAsInt());
        } catch (Exception ex) {
            log.debug("DPI: packet parse skipped: {}", ex.getMessage());
        }
    }

    private void persistClassification(Device device, String sni, String dstIp, int dstPort, int ipTotalBytes) {
        DpiClassifierService.Classification classification = classifier.classify(sni, dstIp, dstPort);
        if (classification.confidence() < effectiveMinConfidence()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        DpiTraffic entry = DpiTraffic.builder()
                .device(device)
                .serviceName(classification.serviceName())
                .trafficCategory(classification.trafficCategory())
                .sniHostname(limit(sni, 255))
                .destinationIp(limit(dstIp, 45))
                .port(dstPort)
                .packetsCount(1)
                .bytesCaptured((long) Math.max(ipTotalBytes, 0))
                .confidence(classification.confidence())
                .classifiedAt(now)
                .build();

        dpiTrafficRepository.save(entry);

        deviceRepository.updateDpiSnapshot(
                device.getId(),
                entry.getServiceName(),
                entry.getTrafficCategory(),
                entry.getSniHostname(),
                entry.getDestinationIp(),
                entry.getPort(),
                now
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("serviceName", entry.getServiceName());
        payload.put("trafficCategory", entry.getTrafficCategory());
        payload.put("sniHostname", entry.getSniHostname());
        payload.put("destinationIp", entry.getDestinationIp());
        payload.put("port", entry.getPort());
        payload.put("confidence", entry.getConfidence());
        payload.put("classifiedAt", entry.getClassifiedAt().toString());

        dashboardWebSocketService.pushMessage("/topic/dpi", payload);
    }

    private String effectiveCaptureFilter() {
        if (captureFilter == null || captureFilter.isBlank()) {
            return "tcp dst port 443";
        }
        return captureFilter.trim();
    }

    private int effectiveMinConfidence() {
        if (minConfidence < 0) {
            return 0;
        }
        if (minConfidence > 100) {
            return 100;
        }
        return minConfidence;
    }

    private Optional<PcapNetworkInterface> findCaptureInterface() throws PcapNativeException {
        List<PcapNetworkInterface> devices = Pcaps.findAllDevs();
        if (devices == null || devices.isEmpty()) {
            return Optional.empty();
        }

        if (interfaceHint != null && !interfaceHint.isBlank()) {
            String hint = interfaceHint.trim().toLowerCase(Locale.ROOT);
            for (PcapNetworkInterface nif : devices) {
                String name = nif.getName() == null ? "" : nif.getName().toLowerCase(Locale.ROOT);
                String description = nif.getDescription() == null ? "" : nif.getDescription().toLowerCase(Locale.ROOT);
                if ((name.contains(hint) || description.contains(hint)) && hasIpv4(nif)) {
                    return Optional.of(nif);
                }
            }
        }

        String local = resolveLocalIpv4();
        if (local != null) {
            for (PcapNetworkInterface nif : devices) {
                boolean hasLocal = nif.getAddresses().stream()
                        .map(PcapAddress::getAddress)
                        .filter(address -> address instanceof Inet4Address)
                        .map(InetAddress::getHostAddress)
                        .anyMatch(local::equals);
                if (hasLocal) {
                    return Optional.of(nif);
                }
            }
        }

        return devices.stream().filter(this::hasIpv4).findFirst();
    }

    private boolean hasIpv4(PcapNetworkInterface nif) {
        return nif.getAddresses().stream()
                .map(PcapAddress::getAddress)
                .anyMatch(address -> address instanceof Inet4Address);
    }

    private String extractSni(byte[] data) {
        // TLS record header (5 bytes): type, version, length
        if (data.length < 9 || u8(data, 0) != 0x16) {
            return null;
        }

        int recordLen = u16(data, 3);
        int maxRecordEnd = Math.min(data.length, 5 + recordLen);

        // Handshake message starts at offset 5.
        if (maxRecordEnd < 9 || u8(data, 5) != 0x01) {
            return null;
        }

        int ptr = 9; // handshake header is 4 bytes, so payload starts at 9

        if (ptr + 34 > maxRecordEnd) {
            return null;
        }

        // client_version (2) + random (32)
        ptr += 34;

        // session_id
        if (ptr + 1 > maxRecordEnd) {
            return null;
        }
        int sessionIdLen = u8(data, ptr);
        ptr += 1;
        if (ptr + sessionIdLen > maxRecordEnd) {
            return null;
        }
        ptr += sessionIdLen;

        // cipher_suites
        if (ptr + 2 > maxRecordEnd) {
            return null;
        }
        int cipherLen = u16(data, ptr);
        ptr += 2;
        if (ptr + cipherLen > maxRecordEnd) {
            return null;
        }
        ptr += cipherLen;

        // compression_methods
        if (ptr + 1 > maxRecordEnd) {
            return null;
        }
        int compressionLen = u8(data, ptr);
        ptr += 1;
        if (ptr + compressionLen > maxRecordEnd) {
            return null;
        }
        ptr += compressionLen;

        // extensions
        if (ptr + 2 > maxRecordEnd) {
            return null;
        }
        int extLen = u16(data, ptr);
        ptr += 2;
        int extEnd = Math.min(maxRecordEnd, ptr + extLen);

        while (ptr + 4 <= extEnd) {
            int extType = u16(data, ptr);
            ptr += 2;
            int extSize = u16(data, ptr);
            ptr += 2;

            if (ptr + extSize > extEnd) {
                break;
            }

            if (extType == 0x0000 && extSize >= 2) {
                int listLen = u16(data, ptr);
                int listPtr = ptr + 2;
                int listEnd = Math.min(ptr + extSize, listPtr + listLen);

                while (listPtr + 3 <= listEnd) {
                    int nameType = u8(data, listPtr);
                    listPtr += 1;
                    int nameLen = u16(data, listPtr);
                    listPtr += 2;

                    if (listPtr + nameLen > listEnd) {
                        break;
                    }

                    if (nameType == 0 && nameLen > 0) {
                        String host = new String(data, listPtr, nameLen, StandardCharsets.US_ASCII)
                                .trim()
                                .toLowerCase(Locale.ROOT);
                        host = host.replaceAll("[^a-z0-9.-]", "");
                        return host.isBlank() ? null : host;
                    }

                    listPtr += nameLen;
                }
            }

            ptr += extSize;
        }

        return null;
    }

    private int u8(byte[] data, int pos) {
        return data[pos] & 0xFF;
    }

    private int u16(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
    }

    private boolean isRecentDuplicate(String srcIp, String dstIp, int dstPort, String sni) {
        String key = srcIp + '|' + dstIp + '|' + dstPort + '|' + sni;
        long now = System.currentTimeMillis();
        Long previous = recentFlows.put(key, now);

        if (recentFlows.size() > 6000) {
            long cutoff = now - 60_000;
            recentFlows.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        return previous != null && (now - previous) < 3_000;
    }

    private boolean isLocalSubnetAddress(String ipAddress) {
        if (ipAddress == null || localPrefix == null) {
            return false;
        }
        if (ipAddress.equals(localIp)) {
            return true;
        }
        return ipAddress.startsWith(localPrefix + ".");
    }

    private String resolveLocalIpv4() {
        try {
            for (NetworkInterface networkInterface : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                for (InetAddress address : java.util.Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private String first3Octets(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private String readableName(PcapNetworkInterface nif) {
        if (nif.getDescription() != null && !nif.getDescription().isBlank()) {
            return nif.getDescription();
        }
        return nif.getName();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void prepareNativeCaptureLibraryOnWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }

        List<String> candidates = new ArrayList<>();
        addIfNotBlank(candidates, configuredWindowsDllDir);
        addIfNotBlank(candidates, System.getenv("NPCAP_DIR"));
        candidates.add("C:\\Windows\\System32\\Npcap");
        candidates.add("C:\\Program Files\\Npcap");
        candidates.add("C:\\Program Files (x86)\\Npcap");
        candidates.add("C:\\Program Files\\WinPcap");
        candidates.add("C:\\Program Files (x86)\\WinPcap");

        for (String dir : candidates) {
            if (!hasPcapDlls(dir)) {
                continue;
            }

            NativeLibrary.addSearchPath("wpcap", dir);
            NativeLibrary.addSearchPath("Packet", dir);
            appendSystemPropertyPath("jna.library.path", dir);
            return;
        }
    }

    private void addIfNotBlank(List<String> items, String value) {
        if (value != null && !value.isBlank()) {
            items.add(value.trim());
        }
    }

    private boolean hasPcapDlls(String dir) {
        if (dir == null || dir.isBlank()) {
            return false;
        }

        File folder = new File(dir);
        if (!folder.isDirectory()) {
            return false;
        }

        return new File(folder, "wpcap.dll").isFile();
    }

    private void appendSystemPropertyPath(String property, String dir) {
        String current = System.getProperty(property, "");
        if (current.contains(dir)) {
            return;
        }

        if (current.isBlank()) {
            System.setProperty(property, dir);
        } else {
            System.setProperty(property, current + File.pathSeparator + dir);
        }
    }
}
