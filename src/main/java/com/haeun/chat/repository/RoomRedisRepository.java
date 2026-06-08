package com.haeun.chat.repository;

import com.haeun.chat.domain.ChatRoom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 채팅방 메타 / 목록을 다루는 Redis 리포지토리.
 *
 * 키 구조:
 *  - "seq:room"     (String, INCR)        : 방 ID 시퀀스
 *  - "rooms"        (Sorted Set, score=createdAt) : 전체 방 ID 목록 (최근 생성 순)
 *  - "room:{id}"    (Hash)                : 방 메타 (name, createdAt, createdBy)
 */
@Repository
public class RoomRedisRepository {

    private static final String SEQ_ROOM = "seq:room";
    private static final String ROOMS_INDEX = "rooms";

    private final StringRedisTemplate redis;

    public RoomRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public ChatRoom create(String name, String createdBy) {
        Long seq = redis.opsForValue().increment(SEQ_ROOM);
        String id = "r" + (seq == null ? 1 : seq);
        long now = System.currentTimeMillis();

        String key = roomKey(id);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("id", id);
        meta.put("name", name);
        meta.put("createdAt", String.valueOf(now));
        meta.put("createdBy", createdBy);
        redis.opsForHash().putAll(key, meta);
        redis.opsForZSet().add(ROOMS_INDEX, id, now);

        return new ChatRoom(id, name, now, createdBy);
    }

    public Optional<ChatRoom> findById(String id) {
        Map<Object, Object> meta = redis.opsForHash().entries(roomKey(id));
        if (meta == null || meta.isEmpty()) return Optional.empty();
        return Optional.of(new ChatRoom(
                id,
                String.valueOf(meta.get("name")),
                Long.parseLong(String.valueOf(meta.get("createdAt"))),
                String.valueOf(meta.get("createdBy"))
        ));
    }

    /** 방과 그 방에 속한 모든 부속 키를 삭제. */
    public void delete(String id) {
        redis.delete(List.of(
                roomKey(id),
                "room:" + id + ":messages",
                "room:" + id + ":msgId",
                "room:" + id + ":members",
                "room:" + id + ":online",
                "room:" + id + ":lastRead"
        ));
        redis.opsForZSet().remove(ROOMS_INDEX, id);
    }

    /** 최근 생성된 방부터 정렬해서 반환. */
    public List<ChatRoom> findAll() {
        Set<String> ids = redis.opsForZSet().reverseRange(ROOMS_INDEX, 0, -1);
        if (ids == null || ids.isEmpty()) return List.of();
        List<ChatRoom> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    private static String roomKey(String id) {
        return "room:" + id;
    }
}
