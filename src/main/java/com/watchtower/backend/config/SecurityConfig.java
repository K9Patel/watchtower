package com.watchtower.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * AJT — Spring Security + BCrypt:
 *
 * - Secures all non-public endpoints behind form-based login
 * - /api/* endpoints are permitted without login (for dashboard AJAX calls)
 * - /login and /static/** are always public
 * - Passwords are stored as BCrypt hashes (cost=10)
 *
 * The admin user matches the BCrypt hash in V1 migration (admin / admin123).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // Same hash as V1 SQL seed: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lvcW
        UserDetails admin = User.builder()
                .username("admin")
                .password("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lvcW")
                .roles("ADMIN")
                .build();

        UserDetails user = User.builder()
                .username("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: API routes used by Vite dashboard (separate origin)
                .requestMatchers("/api/**").permitAll()
                // Public: static assets + login page
                .requestMatchers("/", "/login", "/css/**", "/js/**",
                                 "/static/**", "/favicon.ico").permitAll()
                // Public: raw Servlet endpoint for AJT demo
                .requestMatchers("/diagnosis").permitAll()
                // Everything else requires login
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // Disable CSRF for API routes (React/Vite sends JSON without CSRF tokens)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }
}
