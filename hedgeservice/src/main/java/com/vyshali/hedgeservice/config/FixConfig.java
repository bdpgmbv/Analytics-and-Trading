package com.vyshali.hedgeservice.config;

/*
 * 12/09/2025 - 1:09 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.fix.FixEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import java.io.InputStream;

@Configuration
public class FixConfig {

    @Bean
    public SessionSettings sessionSettings() throws ConfigError {
        // Loads from src/main/resources/quickfix-client.cfg
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("quickfix-client.cfg");
        return new SessionSettings(inputStream);
    }

    @Bean
    public Application fixApplication() {
        return new FixEngine(); // Your FixEngine class
    }

    @Bean
    public MessageStoreFactory messageStoreFactory(SessionSettings settings) {
        return new FileStoreFactory(settings);
    }

    @Bean
    public LogFactory logFactory(SessionSettings settings) {
        return new FileLogFactory(settings);
    }

    @Bean
    public SocketInitiator socketInitiator(Application application, MessageStoreFactory messageStoreFactory, SessionSettings settings, LogFactory logFactory) throws ConfigError {
        return new SocketInitiator(application, messageStoreFactory, settings, logFactory, new DefaultMessageFactory());
    }
}
