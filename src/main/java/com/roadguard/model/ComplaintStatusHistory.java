package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Append-only timeline of a complaint: submission, every status change and every severity change,
 * with who did it and when. Rows are never updated or deleted by the application.
 */
@Entity
@Table(name = "complaint_status_history", indexes = {
        @Index(name = "idx_history_complaint", columnList = "complaint_id"),
        @Index(name = "idx_history_created", columnList = "createdAt")
})
public class ComplaintStatusHistory {
    public static final String SUBMITTED = "SUBMITTED";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String SEVERITY_CHANGE = "SEVERITY_CHANGE";
    public static final String IMPORTED = "IMPORTED";
    public static final String RESOLUTION_EVIDENCE = "RESOLUTION_EVIDENCE";
    public static final String DUPLICATE_MARKED = "DUPLICATE_MARKED";
    public static final String FLAG_DISMISSED = "FLAG_DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @Column(nullable = false)
    private String eventType;

    /** Previous status / severity (null for the first entry). */
    private String fromValue;

    @Column(nullable = false)
    private String toValue;

    @Column(length = 500)
    private String note;

    /** The person responsible: the reporter for SUBMITTED, an administrator for changes. */
    @ManyToOne
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public ComplaintStatusHistory() {}

    public ComplaintStatusHistory(Complaint complaint, String eventType, String fromValue, String toValue,
                                  String note, User changedBy, Instant createdAt) {
        this.complaint = complaint;
        this.eventType = eventType;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.note = note;
        this.changedBy = changedBy;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getId() { return id; }
    public Complaint getComplaint() { return complaint; }
    public String getEventType() { return eventType; }
    public String getFromValue() { return fromValue; }
    public String getToValue() { return toValue; }
    public String getNote() { return note; }
    public User getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
