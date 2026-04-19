package com.watchtower.backend.service;

import com.sun.jna.NativeLibrary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapStat;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.Packet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class PacketCaptureService {

    private static final int SNAPLEN = 65536;
    private static final int READ_TIMEOUT_MS = 10;

    @Value("${watchtower.pcap.windows-dll-dir:}")
    private String configuredWindowsDllDir;

    private final Map<String, AtomicLong> bytesByIp = new ConcurrentHashMap<>();

    private volatile boolean captureAvailable = false;
    private volatile String localIp = null;
    private volatile String localPrefix = null;
    private volatile PcapHandle handle = null;
    private volatile Thread captureThread = null;

    @PostConstruct
    public void startCapture() {
        try {
            prepareNativeCaptureLibraryOnWindows();

            localIp = resolveLocalIpv4();
            localPrefix = localIp != null ? first3Octets(localIp) : null;

            Optional<PcapNetworkInterface> candidate = findBestCaptureInterface();
            if (candidate.isEmpty()) {
                log.warn("PacketCapture: no suitable capture interface found; using fallback estimator.");
                return;
            }

            PcapNetworkInterface nif = candidate.get();
            handle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, READ_TIMEOUT_MS);
            handle.setFilter("ip", BpfProgram.BpfCompileMode.OPTIMIZE);

            PacketListener listener = this::onPacket;
            captureThread = new Thread(() -> {
                try {
                    captureAvailable = true;
                    log.info("PacketCapture: started on interface {}", nif.getName());
                    handle.loop(-1, listener);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (NotOpenException e) {
                    log.warn("PacketCapture: capture handle closed.");
                } catch (Exception e) {
                    log.warn("PacketCapture: stopped due to error: {}", e.getMessage());
                } finally {
                    captureAvailable = false;
                }
            }, "packet-capture-loop");
            captureThread.setDaemon(true);
            captureThread.start();

        } catch (Throwable t) {
            // Typical causes on Windows: missing Npcap or insufficient privileges.
            captureAvailable = false;
            log.warn("PacketCapture: unavailable ({}). Falling back to estimator.", t.getMessage());
        }
    }

    private void prepareNativeCaptureLibraryOnWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
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

            log.info("PacketCapture: configured native library search path: {}", dir);
            return;
        }

        log.warn("PacketCapture: no Windows Npcap/WinPcap DLL directory found. "
                + "Install Npcap with WinPcap API-compatible mode or set watchtower.pcap.windows-dll-dir.");
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

    private void addIfNotBlank(List<String> items, String value) {
        if (value != null && !value.isBlank()) {
            items.add(value.trim());
        }
    }

    @PreDestroy
    public void stopCapture() {
        try {
            if (handle != null && handle.isOpen()) {
                try {
                    handle.breakLoop();
                } catch (NotOpenException ignored) {
                    // no-op
                }
                try {
                    PcapStat stat = handle.getStats();
                    log.info("PacketCapture: stats recv={}, drop={}, ifdrop={}",
                            stat.getNumPacketsReceived(), stat.getNumPacketsDropped(), stat.getNumPacketsDroppedByIf());
                } catch (Exception ignored) {
                    // no-op
                }
                handle.close();
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    public boolean isCaptureAvailable() {
        return captureAvailable;
    }

    // Returns MB observed for a specific IP since last call, then resets that counter.
    public double consumeMegabytesForIp(String ipAddress) {
        AtomicLong counter = bytesByIp.get(ipAddress);
        if (counter == null) {
            return 0.0;
        }
        long bytes = counter.getAndSet(0L);
        return bytes / (1024.0 * 1024.0);
    }

    private void onPacket(Packet packet) {
        IpV4Packet ip = packet.get(IpV4Packet.class);
        if (ip == null) {
            return;
        }

        int totalLen = ip.getHeader().getTotalLengthAsInt();
        String src = ip.getHeader().getSrcAddr().getHostAddress();
        String dst = ip.getHeader().getDstAddr().getHostAddress();

        // Track only local subnet endpoints (the devices we display in dashboard).
        if (isLocalSubnetAddress(src)) {
            bytesByIp.computeIfAbsent(src, k -> new AtomicLong()).addAndGet(totalLen);
        }
        if (isLocalSubnetAddress(dst)) {
            bytesByIp.computeIfAbsent(dst, k -> new AtomicLong()).addAndGet(totalLen);
        }
    }

    private boolean isLocalSubnetAddress(String ip) {
        if (ip == null || localPrefix == null) {
            return false;
        }
        if (ip.equals(localIp)) {
            return true;
        }
        return ip.startsWith(localPrefix + ".");
    }

    private Optional<PcapNetworkInterface> findBestCaptureInterface() throws PcapNativeException {
        List<PcapNetworkInterface> devs = Pcaps.findAllDevs();
        if (devs == null || devs.isEmpty()) {
            return Optional.empty();
        }

        String local = resolveLocalIpv4();
        if (local == null) {
            return Optional.empty();
        }

        for (PcapNetworkInterface dev : devs) {
            if (dev.isLoopBack()) {
                continue;
            }

            boolean hasLocal = dev.getAddresses().stream()
                    .map(PcapAddress::getAddress)
                    .filter(a -> a instanceof Inet4Address)
                    .map(InetAddress::getHostAddress)
                    .anyMatch(local::equals);

            if (hasLocal) {
                return Optional.of(dev);
            }
        }

        return Optional.empty();
    }

    private String resolveLocalIpv4() {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                for (InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private String first3Octets(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }
}
