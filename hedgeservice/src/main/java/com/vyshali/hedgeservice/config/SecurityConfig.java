package com.vyshali.hedgeservice.config;

/*
 * 12/03/2025 - 12:15 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll()
                // Only TRADERS can hit the execute button
                // (Role mapping logic omitted for brevity, assumes Scope validation)
                .requestMatchers("/api/hedge/execute").hasAuthority("SCOPE_trade:execute").anyRequest().authenticated()).oauth2ResourceServer(oauth2 -> oauth2.jwt()); // Validate Token
        return http.build();
    }
}