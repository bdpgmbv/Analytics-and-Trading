package com.vyshali.priceservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time price updates.
 * 
 * Clients can subscribe to:
 * - /topic/prices/{productId} - individual security prices
 * - /topic/prices/all - all price updates
 * - /topic/fx-rates/{currencyPair} - individual FX rates
 * - /topic/fx-rates/all - all FX rate updates
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple broker for topics
        registry.enableSimpleBroker("/topic");
        
        // Prefix for messages from clients
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws/prices")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Pure WebSocket endpoint (no SockJS)
        registry.addEndpoint("/ws/prices")
                .setAllowedOriginPatterns("*");
    }
}
