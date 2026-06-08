package com.haeun.chat.listener;

import com.haeun.chat.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP 세션이 끊어졌을 때 (탭 종료, 네트워크 단절 등)
 * 해당 사용자를 자동으로 방에서 퇴장 처리한다.
 */
@Component
public class WebSocketEventListener {

    private final WsSessionRegistry registry;
    private final ChatService chatService;

    public WebSocketEventListener(WsSessionRegistry registry, ChatService chatService) {
        this.registry = registry;
        this.chatService = chatService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        WsSessionRegistry.Session s = registry.remove(event.getSessionId());
        if (s != null) {
            chatService.leave(s.roomId(), s.user());
        }
    }
}
