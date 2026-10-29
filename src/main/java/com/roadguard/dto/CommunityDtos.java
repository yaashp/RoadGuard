package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {}

    public record AuthorView(Long id, String displayName, boolean self) {}

    public record PostRequest(String title, String body, String category, String hazardReportId) {}

    /** The hazard a discussion is about, with its current official status. Null when the post is not linked. */
    public record LinkedHazard(String reportId, String hazardType, String roadName, String status, String statusLabel, String url) {}
    public record CommentRequest(String body, Long parentId) {}
    public record VoteRequest(String vote) {}

    /** Result of a vote: the new totals and what this user now has. */
    public record VoteResult(int score, int upCount, int downCount, int yourVote) {}

    /** One post in a list. The body is included so the feed can show a preview; the full body comes from the detail call. */
    public record PostSummary(Long id, String title, String preview, String category, String categoryLabel, AuthorView author,
                              int score, int commentCount, Instant createdAt, Instant updatedAt, boolean edited, int yourVote, boolean mine) {}

    public record PostDetail(Long id, String title, String body, String category, String categoryLabel, AuthorView author,
                             int score, int upCount, int downCount, int commentCount, Instant createdAt, Instant updatedAt,
                             boolean edited, boolean deleted, int yourVote, boolean mine, LinkedHazard hazard, List<CommentNode> comments) {}

    /** A comment and its replies, nested. Deleted comments keep their place with a placeholder text. */
    public record CommentNode(Long id, Long parentId, String body, AuthorView author, int score, int yourVote, boolean mine,
                              boolean deleted, boolean edited, Instant createdAt, List<CommentNode> replies) {}

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record CategoryInfo(String key, String label, long postCount) {}

    public record ProfileView(AuthorView author, Instant joinedAt, long postCount, long commentCount, int postScore,
                              List<PostSummary> posts) {}
}
