package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - 2:41 PM
 * @author Vyshali Prabananth Lal
 */

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/trading-realm}")
    private String issuerUri;

    @Bean
    public OpenAPI positionLoaderOpenAPI() {
        return new OpenAPI().info(apiInfo()).servers(servers()).components(securityComponents()).addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }

    private Info apiInfo() {
        return new Info().title("Position Loader API").description("""
                ## Position Management Service for FX Analytics Platform
                
                ### Core Flows
                1. **EOD Load**: Automated position refresh from MSPM after market close
                2. **Intraday Updates**: Real-time position changes from MSPA
                3. **Manual Upload**: CSV/JSON position uploads for non-FS accounts
                
                ### Authentication
                All endpoints require OAuth2 JWT token from Keycloak.
                
                ### Rate Limits
                - EOD endpoints: No limit (triggered once daily)
                - Upload endpoints: 10 requests/minute per user
                - Query endpoints: 100 requests/minute per user
                """).version("1.0.0").contact(new Contact().name("FX Analytics Team").email("fxan-support@company.com").url("https://wiki.internal/fxan")).license(new License().name("Internal Use Only").url("https://legal.internal/software-policy"));
    }

    private List<Server> servers() {
        return List.of(new Server().url("http://localhost:8080").description("Local Development"), new Server().url("https://api.uat.internal/loader").description("UAT Environment"), new Server().url("https://api.internal/loader").description("Production"));
    }

    private Components securityComponents() {
        return new Components().addSecuritySchemes("oauth2", new SecurityScheme().type(SecurityScheme.Type.OAUTH2).description("OAuth2 Authentication via Keycloak").flows(new OAuthFlows().authorizationCode(new OAuthFlow().authorizationUrl(issuerUri + "/protocol/openid-connect/auth").tokenUrl(issuerUri + "/protocol/openid-connect/token").scopes(new Scopes().addString("fxan.read", "Read positions and EOD status").addString("fxan.write", "Upload positions").addString("fxan.ops.write", "Trigger EOD operations").addString("fxan.ops.admin", "Admin operations (DLQ replay)")))));
    }
}