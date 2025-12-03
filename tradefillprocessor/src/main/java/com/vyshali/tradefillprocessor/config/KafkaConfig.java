package com.vyshali.tradefillprocessor.config;

/*
 * 12/03/2025 - 1:13 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@Configuration
public class KafkaConfig {
    // Default Spring Boot Kafka config is usually sufficient for JSON producers
}
