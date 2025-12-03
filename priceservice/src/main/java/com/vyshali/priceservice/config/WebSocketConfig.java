package com.vyshali.priceservice.config;

/*
 * 12/02/2025 - 6:44 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // UI subscribes to /topic/account/{id} to get calculated valuations
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-market-data").setAllowedOriginPatterns("*").withSockJS();
    }
}