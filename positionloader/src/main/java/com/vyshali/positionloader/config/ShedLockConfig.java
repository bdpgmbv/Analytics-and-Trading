package com.vyshali.positionloader.config;

/*
 * 12/04/2025 - 11:43 AM
 * FIXED: This is now the ONLY place with @EnableSchedulerLock
 * @author Vyshali Prabananth Lal
 */

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")  // Default lock duration: 10 minutes
public class ShedLockConfig {

    /**
     * Creates the lock provider using JDBC.
     * <p>
     * REQUIRES: The 'shedlock' table to exist in the database.
     * This is created by 005-missing-tables.sql migration.
     * <p>
     * Table structure:
     * CREATE TABLE shedlock (
     * name VARCHAR(64) NOT NULL PRIMARY KEY,
     * lock_until TIMESTAMP NOT NULL,
     * locked_at TIMESTAMP NOT NULL,
     * locked_by VARCHAR(255) NOT NULL
     * );
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime()  // Uses Postgres time, not App Server time
                .build());
    }
}