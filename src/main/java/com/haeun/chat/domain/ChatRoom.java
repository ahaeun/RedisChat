package com.haeun.chat.domain;

/**
 * 오픈 채팅방 메타데이터.
 * @param id        방 ID (예: "r1", "r2" — Redis INCR 기반)
 * @param name      방 이름
 * @param createdAt 생성 시각 (epoch millis)
 * @param createdBy 생성자 닉네임
 */
public record ChatRoom(String id, String name, long createdAt, String createdBy) {}
