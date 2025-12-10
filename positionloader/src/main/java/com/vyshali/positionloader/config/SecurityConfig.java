package com.vyshali.positionloader.config;

/*
 * 12/02/2025 - 11:07 AM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
        })).build();
    }

    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(AbstractHttpConfigurer::disable).build();
    }
}