package com.watchtower.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.*;

@Slf4j
@Component
@Order(1)
public class JdbcDemo implements CommandLineRunner {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("  AJT UNIT 4 — JDBC DEMO (raw, no Spring/JPA)  ");
        log.info("=================================================");

        try {
            // Step 1: DriverManager opens raw connection — no Spring involved
            // JPA does this automatically via HikariPool underneath
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            log.info("JDBC Connection opened: {}", conn.getMetaData().getDatabaseProductName());

            // Step 2: PreparedStatement with ? placeholder — prevents SQL injection
            // JPA equivalent: deviceRepository.findByIsActiveTrue()
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT device_name, ip_address, device_type " +
                            "FROM device WHERE is_active = ? LIMIT 10");
            ps.setBoolean(1, true);

            // Step 3: Execute query and iterate ResultSet row by row
            ResultSet rs = ps.executeQuery();
            log.info("--- Active Devices via raw JDBC ---");

            int count = 0;
            while (rs.next()) {
                String name = rs.getString("device_name");
                String ip = rs.getString("ip_address");
                String type = rs.getString("device_type");
                log.info("  {} | {} | {}", name, ip, type);
                count++;
            }

            if (count == 0) {
                log.info("  (no devices yet — DataSeeder will add them next)");
            }

            // Step 4: Close in order — ResultSet → Statement → Connection
            // JPA / HikariPool handles all of this automatically
            rs.close();
            ps.close();
            conn.close();

            log.info("JDBC Connection closed manually.");
            log.info("JPA equivalent: deviceRepository.findByIsActiveTrue()");
            log.info("=================================================");

        } catch (SQLException e) {
            log.error("JDBC Demo failed: {}", e.getMessage());
        }
    }
}