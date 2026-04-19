package com.watchtower.backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java DPI classifier.
 *
 * This class intentionally has no Spring annotations or dependencies,
 * so it can be unit-tested in isolation.
 */
public class DpiClassifierService {

    public record Classification(String serviceName, String trafficCategory, short confidence) {}

    private record Rule(String serviceName, String trafficCategory, short confidence, List<String> hostPatterns) {}

    private static final List<Rule> HOST_RULES = List.of(
            new Rule("YOUTUBE", "STREAMING", (short) 95,
                    List.of("googlevideo.com", "youtube.com", "ytimg.com", "youtubei.googleapis.com")),
            new Rule("NETFLIX", "STREAMING", (short) 95,
                    List.of("netflix.com", "nflxvideo.net", "nflximg.net", "nflxso.net")),
            new Rule("WHATSAPP", "VOIP", (short) 92,
                    List.of("whatsapp.net", "whatsapp.com", "mmg.whatsapp.net")),
            new Rule("ZOOM", "VOIP", (short) 92,
                    List.of("zoom.us", "zoom.com", "zoomcdn.net", "xmpp.zoom.us")),
            new Rule("BGMI", "GAMING", (short) 90,
                    List.of("bgmi.in", "pubgmobile.com", "proximabeta.com", "igamecj.com")),
            new Rule("DISCORD", "VOIP", (short) 88,
                    List.of("discord.gg", "discord.com", "discordapp.com", "discord.media")),
            new Rule("SPOTIFY", "STREAMING", (short) 86,
                    List.of("spotify.com", "scdn.co"))
    );

    private static final Map<Integer, Classification> PORT_RULES = Map.of(
            1935, new Classification("RTMP_STREAM", "STREAMING", (short) 72),
            3478, new Classification("RTC_MEDIA", "VOIP", (short) 70),
            3479, new Classification("RTC_MEDIA", "VOIP", (short) 70),
            8801, new Classification("VIDEO_CALL", "VOIP", (short) 70),
            8802, new Classification("VIDEO_CALL", "VOIP", (short) 70),
            5222, new Classification("CHAT_APP", "VOIP", (short) 68)
    );

    private static final Set<Integer> GAME_PORTS = Set.of(27015, 27016, 3074, 3724, 19132, 19133);

    public Classification classify(String sniHostname, String destinationIp, Integer port) {
        String host = normalizeHost(sniHostname);

        if (host != null) {
            for (Rule rule : HOST_RULES) {
                if (matchesAny(host, rule.hostPatterns())) {
                    return new Classification(rule.serviceName(), rule.trafficCategory(), rule.confidence());
                }
            }
        }

        if (port != null) {
            Classification byPort = PORT_RULES.get(port);
            if (byPort != null) {
                return byPort;
            }
            if (GAME_PORTS.contains(port)) {
                return new Classification("GAME_TRAFFIC", "GAMING", (short) 66);
            }
            if (port == 443 || port == 8443) {
                return new Classification("HTTPS_APP", "BROWSING", (short) 52);
            }
        }

        return new Classification("UNKNOWN", "UNKNOWN", (short) 45);
    }

    public static String categoryForService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = serviceName.toUpperCase(Locale.ROOT);
        if (normalized.contains("YOUTUBE") || normalized.contains("NETFLIX") || normalized.contains("SPOTIFY") || normalized.contains("STREAM")) {
            return "STREAMING";
        }
        if (normalized.contains("ZOOM") || normalized.contains("WHATSAPP") || normalized.contains("VOIP") || normalized.contains("DISCORD")) {
            return "VOIP";
        }
        if (normalized.contains("BGMI") || normalized.contains("GAME")) {
            return "GAMING";
        }
        if (normalized.contains("DOWNLOAD")) {
            return "DOWNLOAD";
        }
        if (normalized.contains("HTTPS") || normalized.contains("BROWSE")) {
            return "BROWSING";
        }
        return "UNKNOWN";
    }

    private boolean matchesAny(String host, List<String> patterns) {
        for (String pattern : patterns) {
            if (host.equals(pattern) || host.endsWith("." + pattern) || host.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return null;
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return null;
        }

        // Keep only safe hostname characters.
        String sanitized = normalized.replaceAll("[^a-z0-9.-]", "");
        return sanitized.isBlank() ? null : sanitized;
    }
}
