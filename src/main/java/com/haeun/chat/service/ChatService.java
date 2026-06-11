package com.haeun.chat.service;

import com.haeun.chat.domain.ChatMessage;
import com.haeun.chat.dto.RoomEvent;
import com.haeun.chat.repository.MessageRedisRepository;
import com.haeun.chat.repository.PresenceRedisRepository;
import com.haeun.chat.repository.ReadRedisRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 입장/퇴장/메시지 전송/메시지 히스토리 등 채팅방 내부 동작.
 *
 * 입장 시:
 *  1) members + online 에 추가
 *  2) 입장 전 lastRead 를 oldLastRead 로 저장
 *  3) lastRead = currentMaxId 로 갱신
 *  4) (oldLastRead, currentMaxId] 범위에 대해 UNREAD_UPDATE 이벤트 발행 → 다른 클라이언트의 unread 표시 -1
 *  5) ENTER 이벤트 발행 (participantCount 포함)
 *
 * 메시지 전송 시:
 *  1) 메시지 저장
 *  2) 현재 online 사용자 전원의 lastRead = msgId 로 갱신
 *  3) unreadCount = members - online (보낸이는 online 에 포함됨)
 *  4) MESSAGE 이벤트 발행
 */
@Service
public class ChatService {

    private static final int RECENT_LIMIT = 100;

    private final MessageRedisRepository messages;
    private final PresenceRedisRepository presence;
    private final ReadRedisRepository reads;
    private final SimpMessagingTemplate broker;

    public ChatService(MessageRedisRepository messages,
                       PresenceRedisRepository presence,
                       ReadRedisRepository reads,
                       SimpMessagingTemplate broker) {
        this.messages = messages;
        this.presence = presence;
        this.reads = reads;
        this.broker = broker;
    }

    public synchronized void enter(String roomId, String user) {
        presence.addMember(roomId, user);
        presence.online(roomId, user);

        long oldLastRead = reads.getLastRead(roomId, user);
        long maxId = messages.currentMaxId(roomId);
        if (maxId > oldLastRead) {
            reads.setLastRead(roomId, user, maxId);
            // 다른 사용자 클라이언트에 "이 사람이 (old, max] 메시지를 읽었다" 알림 → unread -1
            broker.convertAndSend(topic(roomId),
                    RoomEvent.unreadUpdate(user, oldLastRead + 1, maxId));
        }

        long participantCount = presence.onlineCount(roomId);
        broker.convertAndSend(topic(roomId), RoomEvent.enter(user, participantCount));
    }

    public synchronized void leave(String roomId, String user) {
        presence.offline(roomId, user);
        long participantCount = presence.onlineCount(roomId);
        broker.convertAndSend(topic(roomId), RoomEvent.leave(user, participantCount));
    }

    public synchronized ChatMessage sendMessage(String roomId, String sender, String content) {
        long id = messages.nextId(roomId);
        long ts = System.currentTimeMillis();
        ChatMessage saving = new ChatMessage(id, roomId, sender, content, ts, 0);
        messages.save(saving);

        // 현재 온라인 사용자 전원은 이 메시지를 즉시 읽은 것으로 처리
        Set<String> online = presence.onlineUsers(roomId);
        reads.setLastReadBulk(roomId, online, id);

        long memberCount = presence.memberCount(roomId);
        long onlineCount = online.size();
        // 안 읽음 수: 멤버 중 온라인이 아닌 사람들 (보낸이는 online 에 이미 포함됨)
        int unread = (int) Math.max(0, memberCount - onlineCount);

        ChatMessage out = saving.withUnreadCount(unread);
        broker.convertAndSend(topic(roomId), RoomEvent.message(out));
        return out;
    }

    /** 채팅방 진입 시 최근 메시지 + 각 메시지의 현재 unreadCount 를 계산해서 반환. */
    public List<ChatMessage> recentMessagesWithUnread(String roomId, String viewer) {
        List<ChatMessage> recent = messages.findRecent(roomId, RECENT_LIMIT);
        if (recent.isEmpty()) return recent;
        Set<String> members = presence.members(roomId);
        List<ChatMessage> result = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            // 보낸이와 viewer 자신은 카운트에서 제외 (viewer 는 지금 보고 있는 것 = 읽은 것)
            // sender == viewer 인 경우 Set.of 가 duplicate 예외를 던지므로 HashSet 사용.
            Set<String> excludes = new HashSet<>();
            excludes.add(m.sender());
            excludes.add(viewer);
            long unread = reads.countUnreadFor(roomId, m.id(), members, excludes);
            result.add(m.withUnreadCount((int) unread));
        }
        return result;
    }

    private static String topic(String roomId) {
        return "/topic/rooms/" + roomId;
    }
}
