package com.watchtower.backend.service;

import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

@Service
public class NetworkScopeResolver {

    public String resolveCurrentNetworkPrefix() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                var addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) {
                            return parts[0] + "." + parts[1] + "." + parts[2];
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return "unknown";
    }
}
