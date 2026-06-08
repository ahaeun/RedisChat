package com.haeun.chat.controller;

import com.haeun.chat.dto.SendMessageRequest;
import com.haeun.chat.listener.WsSessionRegistry;
import com.haeun.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * STOMP 메시지 라우팅.
 *  - /app/rooms/{id}/enter   : 입장
 *  - /app/rooms/{id}/leave   : 퇴장
 *  - /app/rooms/{id}/message : 메시지 전송
 *
 * STOMP CONNECT 시 클라이언트 JS 가 헤더에 nickname 을 실어 보낸 뒤,
 * HandshakeInterceptor 대신 HTTP 세션 attribute 를 그대로 사용한다.
 * (SockJS + spring-session 조합에서 STOMP 세션 attribute 로 접근 가능)
 */
@Controller
public class ChatStompController {

    private final ChatService chatService;
    private final WsSessionRegistry sessionRegistry;

    public ChatStompController(ChatService chatService, WsSessionRegistry sessionRegistry) {
        this.chatService = chatService;
        this.sessionRegistry = sessionRegistry;
    }

    @MessageMapping("/rooms/{id}/enter")
    public void enter(@DestinationVariable("id") String roomId,
                      SimpMessageHeaderAccessor headers) {
        String user = nickname(headers);
        if (user == null) return;
        sessionRegistry.bind(headers.getSessionId(), roomId, user);
        chatService.enter(roomId, user);
    }

    @MessageMapping("/rooms/{id}/leave")
    public void leave(@DestinationVariable("id") String roomId,
                      SimpMessageHeaderAccessor headers) {
        String user = nickname(headers);
        if (user == null) return;
        sessionRegistry.remove(headers.getSessionId());
        chatService.leave(roomId, user);
    }

    @MessageMapping("/rooms/{id}/message")
    public void message(@DestinationVariable("id") String roomId,
                        @Payload SendMessageRequest req,
                        SimpMessageHeaderAccessor headers) {
        String user = nickname(headers);
        if (user == null || req == null || req.content() == null || req.content().isBlank()) return;
        chatService.sendMessage(roomId, user, req.content());
    }

    private static String nickname(SimpMessageHeaderAccessor headers) {
        Map<String, Object> sessionAttrs = headers.getSessionAttributes();
        if (sessionAttrs == null) return null;
        Object n = sessionAttrs.get(LoginController.SESSION_NICKNAME);
        return n == null ? null : n.toString();
    }
}
