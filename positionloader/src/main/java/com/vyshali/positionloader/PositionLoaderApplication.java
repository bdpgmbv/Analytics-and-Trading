package com.vyshali.positionloader;

/*
 * 12/1/25 - 22:54
 * @author Vyshali Prabananth Lal
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties
@ComponentScan(basePackages = {"com.vyshali.positionloader", "com.vyshali.common"})
public class PositionLoaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}