package com.haeun.chat.domain;

/**
 * 로그인 사용자.
 * username 이 곧 채팅 표시명(nickname)로도 쓰인다.
 *
 * @param username     로그인 ID = 표시명
 * @param passwordHash BCrypt 해시
 * @param createdAt    가입 시각 (epoch millis)
 */
public record User(String username, String passwordHash, long createdAt) {}
