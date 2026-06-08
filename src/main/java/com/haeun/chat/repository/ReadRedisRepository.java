package com.haeun.chat.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 사용자별 "마지막으로 읽은 메시지 ID" 관리.
 *
 * 키 구조:
 *  - "room:{id}:lastRead" (Hash, field=user, value=msgId 문자열)
 */
@Repository
public class ReadRedisRepository {

    private final StringRedisTemplate redis;

    public ReadRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long getLastRead(String roomId, String user) {
        Object v = redis.opsForHash().get(key(roomId), user);
        return v == null ? 0L : Long.parseLong(v.toString());
    }

    public void setLastRead(String roomId, String user, long msgId) {
        redis.opsForHash().put(key(roomId), user, String.valueOf(msgId));
    }

    /** 입장 중인 사용자 전원의 lastRead 를 msgId 로 일괄 갱신. */
    public void setLastReadBulk(String roomId, Iterable<String> users, long msgId) {
        Map<String, String> updates = new HashMap<>();
        for (String u : users) updates.put(u, String.valueOf(msgId));
        if (updates.isEmpty()) return;
        redis.opsForHash().putAll(key(roomId), updates);
    }

    /**
     * 메시지 msgId 를 아직 안 읽은 멤버 수 (해당 사용자의 lastRead 가 msgId 미만).
     * @param excludes 카운트에서 제외할 사용자 집합 (보낸이, 현재 보고 있는 viewer 등)
     */
    public long countUnreadFor(String roomId, long msgId, Iterable<String> members, Set<String> excludes) {
        long count = 0;
        for (String m : members) {
            if (excludes.contains(m)) continue;
            if (getLastRead(roomId, m) < msgId) count++;
        }
        return count;
    }

    private static String key(String roomId) { return "room:" + roomId + ":lastRead"; }
}
