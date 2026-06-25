package com.haeun.chat.repository;

import com.haeun.chat.domain.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자(계정) 저장소.
 *
 * 키 구조:
 *  - "users"          (Set)  : 가입한 username 인덱스 (중복 검사 / 전체 조회)
 *  - "user:{username}" (Hash) : username, passwordHash, createdAt
 */
@Repository
public class UserRedisRepository {

    private static final String USERS_INDEX = "users";

    private final StringRedisTemplate redis;

    public UserRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean exists(String username) {
        Boolean b = redis.opsForSet().isMember(USERS_INDEX, username);
        return Boolean.TRUE.equals(b);
    }

    public Optional<User> findByUsername(String username) {
        Map<Object, Object> m = redis.opsForHash().entries(userKey(username));
        if (m == null || m.isEmpty()) return Optional.empty();
        return Optional.of(new User(
                String.valueOf(m.get("username")),
                String.valueOf(m.get("passwordHash")),
                Long.parseLong(String.valueOf(m.get("createdAt")))
        ));
    }

    public void save(User user) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("username", user.username());
        meta.put("passwordHash", user.passwordHash());
        meta.put("createdAt", String.valueOf(user.createdAt()));
        redis.opsForHash().putAll(userKey(user.username()), meta);
        redis.opsForSet().add(USERS_INDEX, user.username());
    }

    private static String userKey(String username) {
        return "user:" + username;
    }
}
