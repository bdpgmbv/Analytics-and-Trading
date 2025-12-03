package com.vyshali.tradefillprocessor.config;

/*
 * 12/03/2025 - 1:13 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.OrderStateDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, OrderStateDTO> orderStateRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, OrderStateDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(OrderStateDTO.class));
        return template;
    }
}
