package com.vyshali.positionloader;

/*
 * 12/1/25 - 22:54
 * FIXED: Added @ComponentScan for common module
 * FIXED: Removed duplicate @EnableSchedulerLock (now only in ShedLockConfig)
 * @author Vyshali Prabananth Lal
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
// FIXED: Removed @EnableSchedulerLock - it's now only in ShedLockConfig.java
// FIXED: Added ComponentScan to include common module for GlobalExceptionHandler
@ComponentScan(basePackages = {"com.vyshali.positionloader", "com.vyshali.common"})
@SpringBootApplication
public class PositionLoaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}