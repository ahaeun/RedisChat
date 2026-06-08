package com.haeun.chat.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 관련 설정. StringRedisTemplate 은 Spring Boot 가 자동 구성하므로
 * 여기서는 메시지 직렬화에 쓰는 ObjectMapper 만 명시한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper chatObjectMapper() {
        return JsonMapper.builder().build();
    }
}
