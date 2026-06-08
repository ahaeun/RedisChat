package com.haeun.chat.repository;

import com.haeun.chat.domain.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 메시지 저장 / 조회. Redis Sorted Set 에 JSON 직렬화한 메시지를 넣고
 * score 는 메시지 ID 를 사용한다 (단조 증가).
 *
 * 키 구조:
 *  - "room:{id}:msgId"    (String, INCR) : 메시지 ID 시퀀스
 *  - "room:{id}:messages" (Sorted Set, score=msgId, value=JSON) : 메시지 리스트
 */
@Repository
public class MessageRedisRepository {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public MessageRedisRepository(StringRedisTemplate redis, ObjectMapper chatObjectMapper) {
        this.redis = redis;
        this.om = chatObjectMapper;
    }

    public long nextId(String roomId) {
        Long v = redis.opsForValue().increment(seqKey(roomId));
        return v == null ? 1L : v;
    }

    /** 현재 방의 최신 메시지 ID (없으면 0). */
    public long currentMaxId(String roomId) {
        String v = redis.opsForValue().get(seqKey(roomId));
        return v == null ? 0L : Long.parseLong(v);
    }

    public void save(ChatMessage msg) {
        try {
            String json = om.writeValueAsString(msg);
            String key = messagesKey(msg.roomId());
            redis.opsForZSet().add(key, json, msg.id());
            redis.expire(key, TTL);
        } catch (JacksonException e) {
            throw new IllegalStateException("메시지 직렬화 실패", e);
        }
    }

    /** 최근 N개 메시지 (오래된 것 → 최신 순). */
    public List<ChatMessage> findRecent(String roomId, int limit) {
        Set<String> jsons = redis.opsForZSet()
                .reverseRange(messagesKey(roomId), 0, Math.max(0, limit - 1));
        if (jsons == null || jsons.isEmpty()) return List.of();
        List<ChatMessage> list = new ArrayList<>(jsons.size());
        for (String j : jsons) {
            list.add(parse(j));
        }
        Collections.reverse(list); // 오래된 → 최신
        return list;
    }

    private ChatMessage parse(String json) {
        try {
            return om.readValue(json, ChatMessage.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("메시지 역직렬화 실패: " + json, e);
        }
    }

    private static String seqKey(String roomId)      { return "room:" + roomId + ":msgId"; }
    private static String messagesKey(String roomId) { return "room:" + roomId + ":messages"; }
}
