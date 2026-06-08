package com.haeun.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 컨텍스트 로드 테스트.
 *
 * 실제 Redis 없이 돌리기 위해 connection factory 를 mock 한다.
 * LettuceConnectionFactory 는 RedisConnectionFactory + ReactiveRedisConnectionFactory 를
 * 동시에 구현하므로 Spring Session(Reactive) 자동 구성도 만족시킨다.
 */
@SpringBootTest
class ChatApplicationTests {

    @MockitoBean
    LettuceConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
    }
}
