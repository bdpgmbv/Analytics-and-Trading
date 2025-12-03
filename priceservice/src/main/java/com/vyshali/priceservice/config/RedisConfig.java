package com.vyshali.priceservice.config;

/*
 * 12/03/2025 - 12:41 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, PriceTickDTO> priceRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, PriceTickDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Keys are Strings ("PRICE:1001")
        template.setKeySerializer(new StringRedisSerializer());

        // Values are JSON Objects
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(PriceTickDTO.class));

        return template;
    }
}
