package com.vyshali.positionloader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Position Loader.
 * 
 * - Development/Test: All endpoints open (no authentication)
 * - Production: OAuth2/JWT authentication required except health endpoints
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Development/Test security - permits all requests.
     * Active when NOT running with 'prod' profile.
     */
    @Bean
    @Profile("!prod")
    @Order(1)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll())
            .build();
    }

    /**
     * Production security - JWT authentication required.
     * Active only when running with 'prod' profile.
     */
    @Bean
    @Profile("prod")
    @Order(1)
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Health and metrics endpoints - public
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/prometheus",
                    "/actuator/info"
                ).permitAll()
                // Swagger/OpenAPI - public
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()
                // Ping endpoint - public
                .requestMatchers("/ping").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {}))
            .build();
    }
}
