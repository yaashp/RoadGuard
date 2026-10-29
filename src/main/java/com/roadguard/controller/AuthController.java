package com.roadguard.controller;

import com.roadguard.dto.AuthDtos.LoginRequest;
import com.roadguard.dto.AuthDtos.RegisterRequest;
import com.roadguard.dto.AuthDtos.UserDto;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Access(AccessLevel.PUBLIC)
public class AuthController {
    private final AuthService auth;
    private final SessionAuth sessions;

    public AuthController(AuthService auth, SessionAuth sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@RequestBody RegisterRequest req, HttpServletRequest http) {
        return UserDto.of(auth.register(req, http));
    }

    @PostMapping("/login")
    public UserDto login(@RequestBody LoginRequest req, HttpServletRequest http) {
        return UserDto.of(auth.login(req, http));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest http) {
        auth.logout(http);
        return Map.of("status", "logged out");
    }

    /** The logged-in user, or 401 when nobody is logged in. */
    @GetMapping("/me")
    @Access(AccessLevel.USER)
    public UserDto me(HttpServletRequest http) {
        return UserDto.of(sessions.requireUser(http));
    }
}
