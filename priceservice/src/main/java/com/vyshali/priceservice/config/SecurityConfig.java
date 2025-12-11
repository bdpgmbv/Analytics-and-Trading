package com.vyshali.priceservice.config;

/*
 * 12/11/2025 - Security Configuration for Price Service
 * @author Vyshali Prabananth Lal
 *
 * Configures OAuth2 resource server for JWT validation.
 * WebSocket endpoints require authentication; actuator endpoints are public.
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF - stateless API
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()

                        // WebSocket endpoints - public for SockJS handshake
                        // Actual subscriptions are secured via message-level security
                        .requestMatchers("/ws-market-data/**").permitAll()

                        // Price query endpoints require authentication
                        .requestMatchers("/api/prices/**").authenticated()
                        .requestMatchers("/api/fx/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // OAuth2 Resource Server with JWT
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                // Custom JWT converter can be added here
                                // .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /*
     * Uncomment and customize if you need role extraction from JWT
     *
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtConverter;
    }
    */
}
