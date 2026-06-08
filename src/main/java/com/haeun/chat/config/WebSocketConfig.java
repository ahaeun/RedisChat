package com.haeun.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP over WebSocket 설정.
 *  - 클라이언트는 /ws-stomp 로 핸드셰이크
 *  - 서버 → 클라이언트 브로드캐스트는 /topic/...
 *  - 클라이언트 → 서버 메시지는 /app/... prefix
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
                // HTTP 세션 attribute (nickname 등) 을 WebSocket 세션으로 복사
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .withSockJS();
    }
}
