package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user_read", columnList = "user_id,readFlag"))
public class Notification {
    public static final String COMPLAINT_SUBMITTED = "COMPLAINT_SUBMITTED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String SEVERITY_CHANGED = "SEVERITY_CHANGED";
    public static final String ADMIN_NEW_COMPLAINT = "ADMIN_NEW_COMPLAINT";
    public static final String HAZARD_FLAGGED = "HAZARD_FLAGGED";
    public static final String HAZARD_VERIFIED = "HAZARD_VERIFIED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** Complaint this notification is about (lets the UI deep-link), if any. */
    private Long complaintRef;

    @Column(nullable = false)
    private boolean readFlag = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(User user, String type, String title, String message, Long complaintRef) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.complaintRef = complaintRef;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getComplaintRef() { return complaintRef; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
