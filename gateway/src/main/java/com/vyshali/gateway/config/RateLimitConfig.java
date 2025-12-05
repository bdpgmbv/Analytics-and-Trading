package com.vyshali.gateway.config;

/*
 * 12/05/2025 - 11:25 AM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
public class RateLimitConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName) // If user is logged in, limit by Username (UUID)
                .defaultIfEmpty(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()); // If anon, limit by IP
    }
}
