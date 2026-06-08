package com.haeun.chat.domain;

/**
 * 채팅 메시지.
 * @param id          방 내부에서 단조 증가하는 메시지 ID (INCR)
 * @param roomId      방 ID
 * @param sender      보낸 사람 닉네임
 * @param content     메시지 본문
 * @param ts          전송 시각 (epoch millis)
 * @param unreadCount 클라이언트로 내보낼 안 읽은 사용자 수 (서버에서 계산)
 */
public record ChatMessage(
        long id,
        String roomId,
        String sender,
        String content,
        long ts,
        int unreadCount
) {
    public ChatMessage withUnreadCount(int count) {
        return new ChatMessage(id, roomId, sender, content, ts, count);
    }
}
