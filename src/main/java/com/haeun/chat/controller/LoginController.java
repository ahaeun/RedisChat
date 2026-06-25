package com.haeun.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 폼 페이지.
 * 실제 POST /login 처리는 Spring Security 의 UsernamePasswordAuthenticationFilter 가 담당한다.
 * 로그아웃(POST /logout) 도 Spring Security 가 처리.
 */
@Controller
public class LoginController {

    @GetMapping("/")
    public String index() {
        return "redirect:/rooms";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }
}
