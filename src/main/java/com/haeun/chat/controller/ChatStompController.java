package com.haeun.chat.controller;

import com.haeun.chat.dto.SendMessageRequest;
import com.haeun.chat.listener.WsSessionRegistry;
import com.haeun.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 라우팅. Spring Security 가 인증된 사용자의 Principal 을
 * STOMP 세션에 자동 주입해주므로 컨트롤러 매개변수로 받기만 하면 된다.
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
                      SimpMessageHeaderAccessor headers,
                      Principal principal) {
        if (principal == null) return;
        String user = principal.getName();
        sessionRegistry.bind(headers.getSessionId(), roomId, user);
        chatService.enter(roomId, user);
    }

    @MessageMapping("/rooms/{id}/leave")
    public void leave(@DestinationVariable("id") String roomId,
                      SimpMessageHeaderAccessor headers,
                      Principal principal) {
        if (principal == null) return;
        sessionRegistry.remove(headers.getSessionId());
        chatService.leave(roomId, principal.getName());
    }

    @MessageMapping("/rooms/{id}/message")
    public void message(@DestinationVariable("id") String roomId,
                        @Payload SendMessageRequest req,
                        Principal principal) {
        if (principal == null || req == null || req.content() == null || req.content().isBlank()) return;
        chatService.sendMessage(roomId, principal.getName(), req.content());
    }
}
