package com.haeun.chat.domain;

/**
 * 채팅방 토픽으로 발행되는 이벤트 종류.
 */
public enum RoomEventType {
    ENTER,          // 누군가 입장 → participantCount 갱신, lastRead 갱신 알림
    LEAVE,          // 누군가 퇴장
    MESSAGE,        // 새 메시지
    UNREAD_UPDATE,  // [minMsgId, maxMsgId] 범위의 메시지가 한 명에게 더 읽힘 (unread -1)
    ROOM_DELETED    // 방이 삭제됨 → 클라이언트는 목록으로 이동
}
