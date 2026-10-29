package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One user's vote on one post or one comment (+1 or -1). Exactly one of post / comment is set.
 * The unique constraints guarantee a user can vote once per item; changing a vote updates this row.
 */
@Entity
@Table(name = "community_votes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_vote_post_user", columnNames = {"post_id", "user_id"}),
        @UniqueConstraint(name = "uq_vote_comment_user", columnNames = {"comment_id", "user_id"})
})
public class CommunityVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private CommunityComment comment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** +1 (upvote) or -1 (downvote). Column is named vote_value because VALUE is a reserved word in H2. */
    @Column(name = "vote_value", nullable = false)
    private int value;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    public CommunityVote() {}

    public CommunityVote(CommunityPost post, CommunityComment comment, User user, int value) {
        this.post = post;
        this.comment = comment;
        this.user = user;
        this.value = value;
    }

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; }
    public CommunityComment getComment() { return comment; }
    public User getUser() { return user; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; this.updatedAt = Instant.now(); }
}
