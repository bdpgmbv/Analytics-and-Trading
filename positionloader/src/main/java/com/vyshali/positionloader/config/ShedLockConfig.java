package com.vyshali.positionloader.config;

/*
 * 12/04/2025 - 11:43 AM
 * @author Vyshali Prabananth Lal
 */

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock; // CRITICAL ANNOTATION
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling; // CRITICAL ANNOTATION
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableScheduling // Enables @Scheduled
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") // Enables ShedLock
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime() // Uses Postgres time, not App Server time
                .build());
    }
}