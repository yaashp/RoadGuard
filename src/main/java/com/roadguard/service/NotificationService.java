package com.roadguard.service;

import com.roadguard.dto.NotificationDtos.NotificationPage;
import com.roadguard.dto.NotificationDtos.NotificationView;
import com.roadguard.exception.ApiException;
import com.roadguard.model.Notification;
import com.roadguard.model.User;
import com.roadguard.model.UserPreference;
import com.roadguard.repository.NotificationRepository;
import com.roadguard.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * In-app notifications only (rows in the notifications table, shown in the bell menu and the
 * notifications page). Nothing is sent by e-mail, SMS or push.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final PreferenceService preferences;

    public NotificationService(NotificationRepository notifications, UserRepository users, PreferenceService preferences) {
        this.notifications = notifications;
        this.users = users;
        this.preferences = preferences;
    }

    /** Creates a notification unless the recipient switched that kind off. Returns whether one was stored. */
    @Transactional
    public boolean notifyUser(User recipient, String type, String title, String message, Long complaintRef) {
        UserPreference prefs = preferences.effective(recipient);
        boolean wanted = switch (type) {
            case Notification.COMPLAINT_SUBMITTED -> prefs.isNotifyOnSubmission();
            case Notification.STATUS_CHANGED, Notification.SEVERITY_CHANGED -> prefs.isNotifyOnStatusChange();
            default -> true;
        };
        if (!wanted) return false;
        notifications.save(new Notification(recipient, type, truncate(title, 120), truncate(message, 500), complaintRef));
        return true;
    }

    @Transactional
    public int notifyAdmins(String type, String title, String message, Long complaintRef, Long exceptUserId) {
        int count = 0;
        for (User admin : users.findByRole(User.ROLE_ADMIN)) {
            if (admin.getId().equals(exceptUserId)) continue;
            if (notifyUser(admin, type, title, message, complaintRef)) count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public NotificationPage list(User user, boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<Notification> result = unreadOnly
                ? notifications.findByUser_IdAndReadFlagFalseOrderByCreatedAtDescIdDesc(user.getId(), pr)
                : notifications.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId(), pr);
        List<NotificationView> items = result.getContent().stream().map(NotificationService::toView).toList();
        return new NotificationPage(items, unreadCount(user), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notifications.countByUser_IdAndReadFlagFalse(user.getId());
    }

    @Transactional
    public NotificationView markRead(User user, Long id) {
        Notification n = notifications.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        n.setReadFlag(true);
        return toView(notifications.save(n));
    }

    @Transactional
    public int markAllRead(User user) {
        List<Notification> unread = notifications.findByUser_IdAndReadFlagFalse(user.getId());
        unread.forEach(n -> n.setReadFlag(true));
        notifications.saveAll(unread);
        return unread.size();
    }

    static NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getComplaintRef(),
                n.isReadFlag(), n.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
