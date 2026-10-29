package com.roadguard.controller;

import com.roadguard.repository.UserRepository;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Access(AccessLevel.PUBLIC)
public class HealthController {
    private final UserRepository users;

    public HealthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "RoadGuard");
        out.put("time", Instant.now());
        try {
            users.count();
            out.put("status", "UP");
            out.put("database", "UP");
        } catch (RuntimeException e) {
            out.put("status", "DEGRADED");
            out.put("database", "DOWN");
        }
        return out;
    }
}
