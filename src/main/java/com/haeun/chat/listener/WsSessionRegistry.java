package com.haeun.chat.listener;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * STOMP 세션 ID → (roomId, user) 매핑.
 * 비정상 종료 시 어떤 방의 누구를 offline 처리할지 알기 위해 사용.
 *
 * 단일 인스턴스 가정. 다중 인스턴스로 확장할 경우 Redis 로 옮기면 된다.
 */
@Component
public class WsSessionRegistry {

    public record Session(String roomId, String user) {}

    private final ConcurrentMap<String, Session> map = new ConcurrentHashMap<>();

    public void bind(String sessionId, String roomId, String user) {
        map.put(sessionId, new Session(roomId, user));
    }

    public Session remove(String sessionId) {
        return map.remove(sessionId);
    }
}
