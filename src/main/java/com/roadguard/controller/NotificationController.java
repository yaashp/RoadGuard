package com.roadguard.controller;

import com.roadguard.dto.NotificationDtos.NotificationPage;
import com.roadguard.dto.NotificationDtos.NotificationView;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Access(AccessLevel.USER)
public class NotificationController {
    private final NotificationService notifications;
    private final SessionAuth sessions;

    public NotificationController(NotificationService notifications, SessionAuth sessions) {
        this.notifications = notifications;
        this.sessions = sessions;
    }

    @GetMapping
    public NotificationPage list(@RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                 HttpServletRequest http) {
        return notifications.list(sessions.requireUser(http), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpServletRequest http) {
        return Map.of("unread", notifications.unreadCount(sessions.requireUser(http)));
    }

    @PostMapping("/{id}/read")
    public NotificationView markRead(@PathVariable("id") Long id, HttpServletRequest http) {
        return notifications.markRead(sessions.requireUser(http), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(HttpServletRequest http) {
        return Map.of("marked", notifications.markAllRead(sessions.requireUser(http)));
    }
}
