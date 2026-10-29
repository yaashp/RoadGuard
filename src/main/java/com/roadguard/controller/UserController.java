package com.roadguard.controller;

import com.roadguard.dto.AuthDtos.ChangePasswordRequest;
import com.roadguard.dto.AuthDtos.UpdateProfileRequest;
import com.roadguard.dto.AuthDtos.UserDto;
import com.roadguard.dto.PreferenceDtos.PreferenceRequest;
import com.roadguard.dto.PreferenceDtos.PreferenceView;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.AuthService;
import com.roadguard.service.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The logged-in user's own profile and settings. There is no endpoint that takes another user's id. */
@RestController
@RequestMapping("/api/users/me")
@Access(AccessLevel.USER)
public class UserController {
    private final SessionAuth sessions;
    private final AuthService auth;
    private final PreferenceService preferences;

    public UserController(SessionAuth sessions, AuthService auth, PreferenceService preferences) {
        this.sessions = sessions;
        this.auth = auth;
        this.preferences = preferences;
    }

    @GetMapping
    public UserDto profile(HttpServletRequest http) {
        return UserDto.of(sessions.requireUser(http));
    }

    @PutMapping
    public UserDto updateProfile(@RequestBody UpdateProfileRequest req, HttpServletRequest http) {
        return UserDto.of(auth.updateProfile(sessions.requireUser(http), req));
    }

    @PostMapping("/password")
    public Map<String, String> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        auth.changePassword(sessions.requireUser(http), req, http);
        return Map.of("status", "password changed");
    }

    @GetMapping("/preferences")
    public PreferenceView preferences(HttpServletRequest http) {
        return preferences.view(sessions.requireUser(http));
    }

    @PutMapping("/preferences")
    public PreferenceView updatePreferences(@RequestBody PreferenceRequest req, HttpServletRequest http) {
        return preferences.update(sessions.requireUser(http), req);
    }
}
