package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationView(Long id, String type, String title, String message,
                                   Long complaintRef, boolean read, Instant createdAt) {}

    public record NotificationPage(List<NotificationView> items, long unreadCount,
                                   int page, int size, long totalItems, int totalPages) {}
}
