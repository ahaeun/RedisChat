package com.haeun.chat.dto;

/** 클라이언트 → 서버 메시지 전송 페이로드. */
public record SendMessageRequest(String content) {}
