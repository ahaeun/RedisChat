package com.haeun.chat.controller;

import com.haeun.chat.domain.ChatMessage;
import com.haeun.chat.domain.ChatRoom;
import com.haeun.chat.dto.RoomLists;
import com.haeun.chat.service.ChatService;
import com.haeun.chat.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
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
    public String list(Principal principal, Model model) {
        String nickname = principal.getName();

        RoomLists rooms = roomService.listFor(nickname);
        model.addAttribute("nickname", nickname);
        model.addAttribute("rooms", rooms);
        model.addAttribute("joinedIds", rooms.joined().stream().map(i -> i.id()).toList());
        return "rooms";
    }

    @PostMapping("/rooms")
    public String create(@RequestParam String name, Principal principal) {
        String nickname = principal.getName();

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "redirect:/rooms";
        ChatRoom room = roomService.create(trimmed, nickname);
        return "redirect:/rooms/" + room.id();
    }

    @GetMapping("/rooms/{id}")
    public String room(@PathVariable String id, Principal principal, Model model) {
        String nickname = principal.getName();

        if (!roomService.isMember(id, nickname)) {
            return "redirect:/rooms";
        }

        ChatRoom room = roomService.findById(id);
        List<ChatMessage> history = chatService.recentMessagesWithUnread(id, nickname);
        model.addAttribute("nickname", nickname);
        model.addAttribute("room", room);
        model.addAttribute("history", history);
        return "room";
    }

    @PostMapping("/rooms/{id}/join")
    public String join(@PathVariable String id, Principal principal) {
        try {
            roomService.join(id, principal.getName());
        } catch (IllegalArgumentException ignored) {
            return "redirect:/rooms";
        }
        return "redirect:/rooms/" + id;
    }

    @PostMapping("/rooms/{id}/leave")
    public String leave(@PathVariable String id, Principal principal) {
        try {
            roomService.leaveRoom(id, principal.getName());
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 방장이거나 이미 삭제된 방 — 그냥 목록으로
        }
        return "redirect:/rooms";
    }

    @PostMapping("/rooms/{id}/delete")
    public String delete(@PathVariable String id, Principal principal) {
        try {
            roomService.delete(id, principal.getName());
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 작성자가 아니거나 이미 삭제된 방 — 그냥 목록으로
        }
        return "redirect:/rooms";
    }
}
