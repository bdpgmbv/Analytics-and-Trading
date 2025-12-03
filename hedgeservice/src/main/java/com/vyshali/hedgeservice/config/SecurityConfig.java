package com.vyshali.hedgeservice.config;

/*
 * 12/03/2025 - 12:15 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    @Profile("!prod")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(csrf -> csrf.disable());
        return http.build();
    }
}