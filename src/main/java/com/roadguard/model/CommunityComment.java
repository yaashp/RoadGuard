package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A comment on a post. parent is null for top-level comments; replies point to their parent comment (nesting). */
@Entity
@Table(name = "community_comments", indexes = @Index(name = "idx_comments_post", columnList = "post_id"))
public class CommunityComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CommunityComment parent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private boolean deleted;

    public CommunityComment() {}

    public CommunityComment(CommunityPost post, CommunityComment parent, User author, String body) {
        this.post = post;
        this.parent = parent;
        this.author = author;
        this.body = body;
    }

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; }
    public CommunityComment getParent() { return parent; }
    public User getAuthor() { return author; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
