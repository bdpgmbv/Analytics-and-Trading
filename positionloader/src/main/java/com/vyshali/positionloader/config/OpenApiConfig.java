package com.vyshali.positionloader.config;

/*
 * SIMPLIFIED: Reduced from 60+ lines to ~20 lines
 *
 * BEFORE: All configuration in Java code
 * AFTER:  Most configuration in application.yml, minimal Java
 *
 * WHY:
 * - YAML is easier to read and modify
 * - No recompilation needed to change API docs
 * - Follows Spring Boot convention over configuration
 */

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Position Loader API", version = "1.0", description = "Loads EOD and intraday positions from MSPM into FXAN database", contact = @Contact(name = "FX Analyzer Team", email = "fx-analyzer@morganstanley.com")), servers = {@Server(url = "/", description = "Current Server")}, tags = {@Tag(name = "EOD", description = "End-of-day position operations"), @Tag(name = "Positions", description = "Position queries"), @Tag(name = "Upload", description = "Manual position upload"), @Tag(name = "Health", description = "Health and monitoring")})
public class OpenApiConfig {
    // All configuration via annotations + application.yml
    // No @Bean methods needed!
}