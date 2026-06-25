package com.haeun.chat.service;

import com.haeun.chat.domain.ChatRoom;
import com.haeun.chat.dto.RoomEvent;
import com.haeun.chat.dto.RoomListItem;
import com.haeun.chat.dto.RoomLists;
import com.haeun.chat.repository.MessageRedisRepository;
import com.haeun.chat.repository.PresenceRedisRepository;
import com.haeun.chat.repository.ReadRedisRepository;
import com.haeun.chat.repository.RoomRedisRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 목록·생성·삭제·조회·멤버십(join/leave) 유스케이스.
 *
 * 멤버십 모델:
 *  - 방을 만든 사용자는 자동으로 멤버.
 *  - 다른 사용자는 명시적으로 join() 을 호출해야 멤버가 됨.
 *  - 채팅방 페이지(/rooms/{id}) 진입 권한은 멤버에게만 있음.
 *  - 멤버는 leaveRoom() 으로 명시적으로 탈퇴 가능 (단, 방장은 삭제만 가능).
 *
 * STOMP enter/leave 는 "지금 페이지를 보고 있다(online)" 와 관련이고,
 * join/leaveRoom 은 "이 방의 멤버다(members)" 와 관련임 — 두 개념을 구분한다.
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
        ChatRoom room = rooms.create(name, createdBy);
        // 방장은 생성과 동시에 멤버
        presence.addMember(room.id(), createdBy);
        return room;
    }

    public ChatRoom findById(String id) {
        return rooms.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방: " + id));
    }

    public boolean isMember(String roomId, String user) {
        return presence.isMember(roomId, user);
    }

    /**
     * 방에 참여(멤버 등록). 이미 멤버면 no-op.
     * 참여 시점에 lastRead 를 현재 maxId 로 맞춰서 "이전 메시지는 모두 읽음" 으로 처리한다.
     */
    public void join(String roomId, String user) {
        findById(roomId); // 존재하지 않는 방이면 예외
        if (presence.isMember(roomId, user)) return;
        presence.addMember(roomId, user);
        long maxId = messages.currentMaxId(roomId);
        reads.setLastRead(roomId, user, maxId);
        // 그 방에 있는 다른 멤버들에게 "○○님이 입장했습니다" 알림
        broker.convertAndSend("/topic/rooms/" + roomId, RoomEvent.join(user));
    }

    /**
     * 방에서 나감(멤버 해지). 방장은 호출 불가 (삭제만 가능).
     * 입장 중이었다면 online 에서도 제거하고 LEAVE 이벤트를 발행해 참가자 수를 갱신한다.
     */
    public void leaveRoom(String roomId, String user) {
        ChatRoom room = findById(roomId);
        if (room.createdBy().equals(user)) {
            throw new IllegalStateException("방장은 방을 나갈 수 없습니다. 방을 삭제해 주세요.");
        }
        if (!presence.isMember(roomId, user)) return;

        boolean wasOnline = presence.isOnline(roomId, user);
        long oldLastRead = reads.getLastRead(roomId, user);
        long maxId = messages.currentMaxId(roomId);

        presence.offline(roomId, user);
        presence.removeMember(roomId, user);
        reads.removeLastRead(roomId, user);

        // 이 사용자가 못 본 메시지가 있었다면, 더 이상 멤버가 아니므로 unread 카운트에서 빠진다.
        // 다른 클라이언트(보낸이) 화면의 unread 표시를 그 범위만큼 -1 해준다.
        if (maxId > oldLastRead) {
            broker.convertAndSend("/topic/rooms/" + roomId,
                    RoomEvent.unreadUpdate(user, oldLastRead + 1, maxId));
        }

        if (wasOnline) {
            long count = presence.onlineCount(roomId);
            broker.convertAndSend("/topic/rooms/" + roomId, RoomEvent.leave(user, count));
        }

        // "○○님이 나갔습니다" 시스템 메시지 (ENTER/LEAVE 와 별개)
        broker.convertAndSend("/topic/rooms/" + roomId, RoomEvent.leftRoom(user));
    }

    public void delete(String id, String requester) {
        ChatRoom room = findById(id);
        if (!room.createdBy().equals(requester)) {
            throw new IllegalStateException("작성자만 방을 삭제할 수 있습니다");
        }
        broker.convertAndSend("/topic/rooms/" + id, RoomEvent.roomDeleted());
        rooms.delete(id);
    }

    /**
     * 사용자 관점에서 본 방 목록.
     *  - joined:    현재 사용자가 참여 중인 방 (참가자 수 + 안 읽은 메시지 수 포함)
     *  - available: 아직 참여하지 않은 방 (참가자 수 + 작성자만 표시)
     */
    public RoomLists listFor(String user) {
        List<ChatRoom> all = rooms.findAll();
        List<RoomListItem> joined = new ArrayList<>();
        List<RoomListItem> available = new ArrayList<>();
        for (ChatRoom r : all) {
            long participants = presence.onlineCount(r.id());
            if (presence.isMember(r.id(), user)) {
                long maxId = messages.currentMaxId(r.id());
                long lastRead = reads.getLastRead(r.id(), user);
                long unread = Math.max(0, maxId - lastRead);
                joined.add(new RoomListItem(r.id(), r.name(), participants, unread, r.createdBy()));
            } else {
                available.add(new RoomListItem(r.id(), r.name(), participants, 0, r.createdBy()));
            }
        }
        return new RoomLists(joined, available);
    }
}
