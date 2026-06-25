package com.haeun.chat.dto;

import com.haeun.chat.domain.ChatMessage;
import com.haeun.chat.domain.RoomEventType;

/**
 * 채팅방 토픽(/topic/rooms/{id})으로 발행되는 이벤트.
 * 종류에 따라 일부 필드가 null 일 수 있다.
 *
 * @param type             ENTER / LEAVE / MESSAGE / UNREAD_UPDATE
 * @param user             ENTER/LEAVE 의 주체
 * @param participantCount ENTER/LEAVE 시 현재 입장 인원 수
 * @param message          MESSAGE 시 메시지 본문
 * @param minMsgId         UNREAD_UPDATE 시 갱신 범위(포함)
 * @param maxMsgId         UNREAD_UPDATE 시 갱신 범위(포함)
 */
public record RoomEvent(
        RoomEventType type,
        String user,
        Long participantCount,
        ChatMessage message,
        Long minMsgId,
        Long maxMsgId
) {
    public static RoomEvent enter(String user, long participantCount) {
        return new RoomEvent(RoomEventType.ENTER, user, participantCount, null, null, null);
    }

    public static RoomEvent leave(String user, long participantCount) {
        return new RoomEvent(RoomEventType.LEAVE, user, participantCount, null, null, null);
    }

    public static RoomEvent message(ChatMessage msg) {
        return new RoomEvent(RoomEventType.MESSAGE, msg.sender(), null, msg, null, null);
    }

    public static RoomEvent unreadUpdate(String reader, long minMsgId, long maxMsgId) {
        return new RoomEvent(RoomEventType.UNREAD_UPDATE, reader, null, null, minMsgId, maxMsgId);
    }

    public static RoomEvent roomDeleted() {
        return new RoomEvent(RoomEventType.ROOM_DELETED, null, null, null, null, null);
    }

    public static RoomEvent join(String user) {
        return new RoomEvent(RoomEventType.JOIN, user, null, null, null, null);
    }

    public static RoomEvent leftRoom(String user) {
        return new RoomEvent(RoomEventType.LEAVE_ROOM, user, null, null, null, null);
    }
}
