package com.haeun.chat.dto;

/**
 * 채팅방 목록 화면(/rooms)에 표시할 항목.
 *
 * @param id               방 ID
 * @param name             방 이름
 * @param participantCount 현재 입장 인원
 * @param unreadCount      현재 로그인한 사용자가 이 방에서 안 읽은 메시지 수
 * @param createdBy        방을 만든 사용자 (삭제 권한 판정용)
 */
public record RoomListItem(
        String id,
        String name,
        long participantCount,
        long unreadCount,
        String createdBy
) {}
