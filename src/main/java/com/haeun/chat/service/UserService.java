package com.haeun.chat.service;

import com.haeun.chat.domain.User;
import com.haeun.chat.repository.UserRedisRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원가입 등 사용자 도메인 유스케이스.
 * 로그인 검증 자체는 Spring Security 가 RedisUserDetailsService 를 통해 수행한다.
 */
@Service
public class UserService {

    private static final int MIN_USERNAME = 2;
    private static final int MAX_USERNAME = 20;
    private static final int MIN_PASSWORD = 4;

    private final UserRedisRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRedisRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public void signup(String username, String rawPassword) {
        String name = username == null ? "" : username.trim();
        String pw = rawPassword == null ? "" : rawPassword;

        if (name.length() < MIN_USERNAME || name.length() > MAX_USERNAME) {
            throw new IllegalArgumentException("닉네임은 " + MIN_USERNAME + "~" + MAX_USERNAME + "자여야 합니다.");
        }
        if (pw.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다.");
        }
        if (users.exists(name)) {
            throw new IllegalStateException("이미 사용 중인 닉네임입니다.");
        }
        users.save(new User(name, encoder.encode(pw), System.currentTimeMillis()));
    }
}
