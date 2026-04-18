package com.watchtower.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * AJT Unit 3 — Java Networking: WebSocket Configuration
 *
 * Enables STOMP (Simple Text Oriented Messaging Protocol) over WebSocket
 * with SockJS fallback for browsers that don't support native WebSocket.
 *
 * Architecture:
 *   React frontend → connects to ws://localhost:8080/ws (SockJS)
 *   Backend pushes to /topic/dashboard    → dashboard stats every 10s
 *                      /topic/alerts      → instant when DiagnosisEngine fires
 *                      /topic/devices     → instant when device status changes
 *
 * Why STOMP over raw WebSocket?
 *   - Built-in pub/sub channels (topics)
 *   - Spring's SimpMessagingTemplate handles message routing
 *   - @stomp/stompjs client on React side handles reconnect automatically
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Register the STOMP endpoint that clients connect to.
     * SockJS provides fallback for browsers without WebSocket support.
     * allowedOrigins("*") is fine for development; restrict in production.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // allows React dev server on :5173
                .withSockJS();
    }

    /**
     * Configure the in-memory message broker.
     *   /topic  → broadcast to all subscribers (one-to-many)
     *   /app    → prefix for @MessageMapping controller methods (not used here
     *              but required for two-way messaging if added later)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
