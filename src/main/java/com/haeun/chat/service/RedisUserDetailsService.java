package com.haeun.chat.service;

import com.haeun.chat.repository.UserRedisRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 가 로그인 검증할 때 호출하는 콜백.
 * Redis 에서 사용자 정보를 조회해 UserDetails 형태로 반환한다.
 */
@Service
public class RedisUserDetailsService implements UserDetailsService {

    private final UserRedisRepository users;

    public RedisUserDetailsService(UserRedisRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
                .map(u -> org.springframework.security.core.userdetails.User.builder()
                        .username(u.username())
                        .password(u.passwordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
