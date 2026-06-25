package com.haeun.chat.dto;

import java.util.List;

/**
 * 채팅방 목록 화면용 — 내가 참여한 방과 아직 참여하지 않은 방을 분리해서 담는다.
 */
public record RoomLists(
        List<RoomListItem> joined,
        List<RoomListItem> available
) {}
