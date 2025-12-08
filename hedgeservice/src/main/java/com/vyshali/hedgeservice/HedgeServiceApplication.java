package com.vyshali.hedgeservice;

/*
 * 12/03/2025 - 12:14 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching; // <--- NEW

@EnableCaching // <--- NEW
@SpringBootApplication
public class HedgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HedgeServiceApplication.class, args);
    }
}