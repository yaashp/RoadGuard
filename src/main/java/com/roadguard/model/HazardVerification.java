package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One citizen's opinion about one hazard: still there, or no longer there. A user has at most one vote per hazard
 * (changing it updates this row). Votes never change a report's official status; they only feed counts, flags for
 * administrators, route scoring confidence and impact points.
 */
@Entity
@Table(name = "hazard_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uq_verification_user_hazard", columnNames = {"road_issue_id", "user_id"}),
        indexes = @Index(name = "idx_verification_user_time", columnList = "user_id,createdAt"))
public class HazardVerification {
    public static final String STILL_THERE = "STILL_THERE";
    public static final String GONE = "GONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "road_issue_id")
    private RoadIssue roadIssue;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String vote;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public HazardVerification() {}

    public HazardVerification(RoadIssue roadIssue, User user, String vote) {
        this.roadIssue = roadIssue;
        this.user = user;
        this.vote = vote;
    }

    public Long getId() { return id; }
    public RoadIssue getRoadIssue() { return roadIssue; }
    public User getUser() { return user; }
    public String getVote() { return vote; }
    public void setVote(String vote) { this.vote = vote; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    /** When this opinion was last given (first vote or a change of mind). */
    public Instant votedAt() { return updatedAt != null ? updatedAt : createdAt; }
}
