package com.haeun.chat.service;

import com.haeun.chat.domain.ChatMessage;
import com.haeun.chat.dto.RoomEvent;
import com.haeun.chat.repository.MessageRedisRepository;
import com.haeun.chat.repository.PresenceRedisRepository;
import com.haeun.chat.repository.ReadRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 단위 테스트 — Redis 의존성은 모두 mock.
 * 핵심 로직: 입장/메시지 전송 시 unread 계산 및 이벤트 발행이 맞는지 검증.
 */
class ChatServiceTest {

    MessageRedisRepository messages = mock(MessageRedisRepository.class);
    PresenceRedisRepository presence = mock(PresenceRedisRepository.class);
    ReadRedisRepository reads = mock(ReadRedisRepository.class);
    SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);

    ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(messages, presence, reads, broker);
    }

    @Test
    void sendMessage_unreadCount는_멤버수_빼기_온라인수() {
        // given: 멤버 5명, 온라인 2명 (alice, bob 이 접속중)
        when(messages.nextId("r1")).thenReturn(42L);
        when(presence.onlineUsers("r1")).thenReturn(Set.of("alice", "bob"));
        when(presence.memberCount("r1")).thenReturn(5L);

        // when
        ChatMessage out = service.sendMessage("r1", "alice", "hello");

        // then
        assertThat(out.id()).isEqualTo(42L);
        assertThat(out.sender()).isEqualTo("alice");
        assertThat(out.unreadCount()).isEqualTo(3); // 5 - 2 = 3

        verify(messages).save(any(ChatMessage.class));
        verify(reads).setLastReadBulk(eq("r1"), any(), eq(42L));
        verify(broker).convertAndSend(eq("/topic/rooms/r1"), any(RoomEvent.class));
    }

    @Test
    void enter_입장하면_lastRead_갱신되고_UNREAD_UPDATE와_ENTER_이벤트가_발행된다() {
        // given: 이전에 5번까지 봤고, 지금 최신은 10번
        when(reads.getLastRead("r1", "alice")).thenReturn(5L);
        when(messages.currentMaxId("r1")).thenReturn(10L);
        when(presence.onlineCount("r1")).thenReturn(3L);

        // when
        service.enter("r1", "alice");

        // then: lastRead 가 10 으로 갱신되어야 한다
        verify(reads).setLastRead("r1", "alice", 10L);

        // 두 이벤트 (UNREAD_UPDATE + ENTER) 가 발행돼야 한다
        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(broker, atLeastOnce()).convertAndSend(eq("/topic/rooms/r1"), captor.capture());

        List<RoomEvent> events = captor.getAllValues();
        assertThat(events).hasSize(2);

        RoomEvent unread = events.get(0);
        assertThat(unread.minMsgId()).isEqualTo(6L);
        assertThat(unread.maxMsgId()).isEqualTo(10L);

        RoomEvent enter = events.get(1);
        assertThat(enter.user()).isEqualTo("alice");
        assertThat(enter.participantCount()).isEqualTo(3L);
    }

    @Test
    void enter_새_방이라_읽을게_없으면_UNREAD_UPDATE는_생략된다() {
        when(reads.getLastRead(anyString(), anyString())).thenReturn(0L);
        when(messages.currentMaxId(anyString())).thenReturn(0L);
        when(presence.onlineCount(anyString())).thenReturn(1L);

        service.enter("r9", "first");

        // ENTER 이벤트만 발행
        verify(broker).convertAndSend(eq("/topic/rooms/r9"), any(RoomEvent.class));
    }
}
