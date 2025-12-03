package com.vyshali.mockupstream;

/*
 * 12/02/2025 - 1:53 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync // Critical for background price generation
@SpringBootApplication
public class MockUpstreamApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockUpstreamApplication.class, args);
    }
}