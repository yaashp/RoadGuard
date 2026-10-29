package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A community post. Votes and comment counts are kept as columns so lists can be sorted by Hot and Top in the database. */
@Entity
@Table(name = "community_posts", indexes = {
        @Index(name = "idx_posts_created", columnList = "createdAt"),
        @Index(name = "idx_posts_category", columnList = "category")
})
public class CommunityPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(length = 5000)
    private String body;

    /** One of the fixed topics in CommunityCategory. */
    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private int score;          // upvotes - downvotes
    @Column(nullable = false)
    private int upCount;
    @Column(nullable = false)
    private int downCount;
    @Column(nullable = false)
    private int commentCount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private boolean deleted;    // soft delete: comments stay readable as "[deleted]" under it

    /** Optional link to the hazard report this discussion is about. Nullable: most posts have none. */
    private Long hazardComplaintId;

    public Long getHazardComplaintId() { return hazardComplaintId; }
    public void setHazardComplaintId(Long v) { this.hazardComplaintId = v; }

    public CommunityPost() {}

    public CommunityPost(User author, String title, String body, String category) {
        this.author = author;
        this.title = title;
        this.body = body;
        this.category = category;
    }

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getUpCount() { return upCount; }
    public void setUpCount(int upCount) { this.upCount = upCount; }
    public int getDownCount() { return downCount; }
    public void setDownCount(int downCount) { this.downCount = downCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
