package com.vyshali.hedgeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.fxanalyzer.hedgeservice",
    "com.fxanalyzer.common"
})
@EnableCaching
@EnableScheduling
public class HedgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HedgeServiceApplication.class, args);
    }
}
