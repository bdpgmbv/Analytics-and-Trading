package com.vyshali.priceservice.config;

/*
 * 12/02/2025 - 6:44 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // PERFECTION: Heartbeat (send 10s, receive 10s)
        // Prevents AWS Load Balancers / F5 from killing idle connections.
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000});

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-market-data")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}