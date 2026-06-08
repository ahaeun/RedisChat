package com.haeun.chat.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 닉네임만 입력받는 단순 로그인.
 * 세션은 spring-session-data-redis 에 의해 Redis 에 저장된다.
 */
@Controller
public class LoginController {

    public static final String SESSION_NICKNAME = "nickname";

    @GetMapping("/")
    public String index(HttpSession session) {
        return session.getAttribute(SESSION_NICKNAME) == null ? "redirect:/login" : "redirect:/rooms";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String nickname, HttpSession session) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) return "redirect:/login";
        session.setAttribute(SESSION_NICKNAME, trimmed);
        return "redirect:/rooms";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
