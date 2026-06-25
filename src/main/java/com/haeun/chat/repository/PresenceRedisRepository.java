package com.haeun.chat.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * 방 멤버십과 현재 접속 상태 관리.
 *
 * 키 구조:
 *  - "room:{id}:members" (Set) : 한 번이라도 입장했던 사용자 (안읽음 계산 대상)
 *  - "room:{id}:online"  (Set) : 현재 WebSocket 으로 접속 중인 사용자 (참가자 수)
 */
@Repository
public class PresenceRedisRepository {

    private final StringRedisTemplate redis;

    public PresenceRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void addMember(String roomId, String user) {
        redis.opsForSet().add(membersKey(roomId), user);
    }

    public void removeMember(String roomId, String user) {
        redis.opsForSet().remove(membersKey(roomId), user);
    }

    public boolean isMember(String roomId, String user) {
        Boolean b = redis.opsForSet().isMember(membersKey(roomId), user);
        return Boolean.TRUE.equals(b);
    }

    public long memberCount(String roomId) {
        Long c = redis.opsForSet().size(membersKey(roomId));
        return c == null ? 0 : c;
    }

    public Set<String> members(String roomId) {
        Set<String> s = redis.opsForSet().members(membersKey(roomId));
        return s == null ? Set.of() : s;
    }

    public boolean isOnline(String roomId, String user) {
        Boolean b = redis.opsForSet().isMember(onlineKey(roomId), user);
        return Boolean.TRUE.equals(b);
    }

    public void online(String roomId, String user) {
        redis.opsForSet().add(onlineKey(roomId), user);
    }

    public void offline(String roomId, String user) {
        redis.opsForSet().remove(onlineKey(roomId), user);
    }

    public long onlineCount(String roomId) {
        Long c = redis.opsForSet().size(onlineKey(roomId));
        return c == null ? 0 : c;
    }

    public Set<String> onlineUsers(String roomId) {
        Set<String> s = redis.opsForSet().members(onlineKey(roomId));
        return s == null ? Set.of() : s;
    }

    private static String membersKey(String roomId) { return "room:" + roomId + ":members"; }
    private static String onlineKey(String roomId)  { return "room:" + roomId + ":online"; }
}
