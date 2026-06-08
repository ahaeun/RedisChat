package com.haeun.chat.service;

import com.haeun.chat.domain.ChatRoom;
import com.haeun.chat.dto.RoomEvent;
import com.haeun.chat.dto.RoomListItem;
import com.haeun.chat.repository.MessageRedisRepository;
import com.haeun.chat.repository.PresenceRedisRepository;
import com.haeun.chat.repository.ReadRedisRepository;
import com.haeun.chat.repository.RoomRedisRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 목록·생성·삭제·조회 유스케이스.
 * "방 단위 안 읽음 메시지 수" 계산도 여기서 담당한다.
 */
@Service
public class RoomService {

    private final RoomRedisRepository rooms;
    private final PresenceRedisRepository presence;
    private final MessageRedisRepository messages;
    private final ReadRedisRepository reads;
    private final SimpMessagingTemplate broker;

    public RoomService(RoomRedisRepository rooms,
                       PresenceRedisRepository presence,
                       MessageRedisRepository messages,
                       ReadRedisRepository reads,
                       SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.presence = presence;
        this.messages = messages;
        this.reads = reads;
        this.broker = broker;
    }

    public ChatRoom create(String name, String createdBy) {
        return rooms.create(name, createdBy);
    }

    public ChatRoom findById(String id) {
        return rooms.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방: " + id));
    }

    /** 방 작성자만 삭제 가능. 입장 중인 클라이언트들은 ROOM_DELETED 이벤트로 목록으로 보낸다. */
    public void delete(String id, String requester) {
        ChatRoom room = findById(id);
        if (!room.createdBy().equals(requester)) {
            throw new IllegalStateException("작성자만 방을 삭제할 수 있습니다");
        }
        // 입장중인 클라이언트들에게 먼저 알리고 Redis 정리
        broker.convertAndSend("/topic/rooms/" + id, RoomEvent.roomDeleted());
        rooms.delete(id);
    }

    /** 현재 사용자 관점에서 본 방 목록 (참가자 수 + 안 읽은 메시지 수 + 작성자 포함). */
    public List<RoomListItem> listFor(String user) {
        List<ChatRoom> all = rooms.findAll();
        List<RoomListItem> result = new ArrayList<>(all.size());
        for (ChatRoom r : all) {
            long participants = presence.onlineCount(r.id());
            long maxId = messages.currentMaxId(r.id());
            long lastRead = reads.getLastRead(r.id(), user);
            long unread = Math.max(0, maxId - lastRead);
            result.add(new RoomListItem(r.id(), r.name(), participants, unread, r.createdBy()));
        }
        return result;
    }
}
