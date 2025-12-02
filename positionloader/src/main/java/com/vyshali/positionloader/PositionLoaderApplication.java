package com.vyshali.positionloader;

/*
 * 12/1/25 - 22:54
 * @author Vyshali Prabananth Lal
 */

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@SpringBootApplication
public class PositionLoaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}
