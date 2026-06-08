package com.haeun.chat.controller;

import com.haeun.chat.domain.ChatMessage;
import com.haeun.chat.domain.ChatRoom;
import com.haeun.chat.dto.RoomListItem;
import com.haeun.chat.service.ChatService;
import com.haeun.chat.service.RoomService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class RoomController {

    private final RoomService roomService;
    private final ChatService chatService;

    public RoomController(RoomService roomService, ChatService chatService) {
        this.roomService = roomService;
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public String list(HttpSession session, Model model) {
        String nickname = currentUser(session);
        if (nickname == null) return "redirect:/login";

        List<RoomListItem> rooms = roomService.listFor(nickname);
        model.addAttribute("nickname", nickname);
        model.addAttribute("rooms", rooms);
        return "rooms";
    }

    @PostMapping("/rooms")
    public String create(@RequestParam String name, HttpSession session) {
        String nickname = currentUser(session);
        if (nickname == null) return "redirect:/login";

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "redirect:/rooms";
        ChatRoom room = roomService.create(trimmed, nickname);
        return "redirect:/rooms/" + room.id();
    }

    @GetMapping("/rooms/{id}")
    public String room(@PathVariable String id, HttpSession session, Model model) {
        String nickname = currentUser(session);
        if (nickname == null) return "redirect:/login";

        ChatRoom room = roomService.findById(id);
        List<ChatMessage> history = chatService.recentMessagesWithUnread(id, nickname);
        model.addAttribute("nickname", nickname);
        model.addAttribute("room", room);
        model.addAttribute("history", history);
        return "room";
    }

    @PostMapping("/rooms/{id}/delete")
    public String delete(@PathVariable String id, HttpSession session) {
        String nickname = currentUser(session);
        if (nickname == null) return "redirect:/login";

        try {
            roomService.delete(id, nickname);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 작성자가 아니거나 이미 삭제된 방 — 그냥 목록으로
        }
        return "redirect:/rooms";
    }

    private static String currentUser(HttpSession session) {
        Object n = session.getAttribute(LoginController.SESSION_NICKNAME);
        return n == null ? null : n.toString();
    }
}
