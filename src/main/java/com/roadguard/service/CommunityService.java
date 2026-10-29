package com.roadguard.service;

import com.roadguard.dto.CommunityDtos.*;
import com.roadguard.dto.CommunityDtos.LinkedHazard;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.exception.ValidationException;
import com.roadguard.model.CommunityCategory;
import com.roadguard.model.CommunityComment;
import com.roadguard.model.CommunityPost;
import com.roadguard.model.CommunityVote;
import com.roadguard.model.Complaint;
import com.roadguard.model.User;
import com.roadguard.repository.CommunityCommentRepository;
import com.roadguard.repository.CommunityPostRepository;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.CommunityVoteRepository;
import com.roadguard.repository.UserRepository;
import com.roadguard.security.SlidingWindowCounter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The road-safety community: posts, comments, nested replies and votes.
 * Rules enforced here (not in the browser): only the author can edit or delete; a user votes once per item and may change
 * the vote; the score on each post and comment is recomputed from the vote rows; deleted posts keep their comments as placeholders.
 */
@Service
public class CommunityService {
    static final String DELETED_TEXT = "[deleted]";
    private static final int TITLE_MAX = 140, BODY_MAX = 5000, COMMENT_MAX = 2000, PAGE_MAX = 50;

    private final CommunityPostRepository posts;
    private final CommunityCommentRepository comments;
    private final CommunityVoteRepository votes;
    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final SlidingWindowCounter writeLimiter = new SlidingWindowCounter(60, 3_600_000L);
    private final SlidingWindowCounter voteLimiter = new SlidingWindowCounter(300, 3_600_000L);

    public CommunityService(CommunityPostRepository posts, CommunityCommentRepository comments,
                            CommunityVoteRepository votes, UserRepository users, ComplaintRepository complaints) {
        this.complaints = complaints;
        this.posts = posts;
        this.comments = comments;
        this.votes = votes;
        this.users = users;
    }

    // ------------------------------------------------------------ categories

    public List<CategoryInfo> categories() {
        Map<String, Long> counts = posts.findAll().stream().filter(p -> !p.isDeleted())
                .collect(Collectors.groupingBy(CommunityPost::getCategory, Collectors.counting()));
        List<CategoryInfo> out = new ArrayList<>();
        for (CommunityCategory c : CommunityCategory.values()) out.add(new CategoryInfo(c.key(), c.label(), counts.getOrDefault(c.key(), 0L)));
        return out;
    }

    // ------------------------------------------------------------ posts

    @Transactional
    public PostDetail createPost(User author, PostRequest req) {
        if (!writeLimiter.tryAcquire("write:" + author.getId())) throw ApiException.tooMany("You are posting too quickly. Please wait a little while.");
        String title = Inputs.clean(req.title()), body = Inputs.clean(req.body());
        CommunityCategory cat = CommunityCategory.fromKey(req.category()).orElse(null);
        Validation v = new Validation();
        v.check(title.length() >= 5 && title.length() <= TITLE_MAX, "title", "Title must be 5-140 characters.");
        v.check(body.length() <= BODY_MAX, "body", "Post text can be at most 5000 characters.");
        v.check(cat != null, "category", "Choose a topic for your post.");
        Long hazardId = resolveHazard(req.hazardReportId(), v);
        v.throwIfInvalid();
        CommunityPost p = new CommunityPost(author, title, body, cat.key());
        p.setHazardComplaintId(hazardId);
        p = posts.save(p);
        return detail(p, author, false);
    }

    @Transactional
    public PostDetail editPost(User user, Long id, PostRequest req) {
        CommunityPost p = ownedPost(user, id);
        String title = Inputs.clean(req.title()), body = Inputs.clean(req.body());
        Validation v = new Validation();
        v.check(title.length() >= 5 && title.length() <= TITLE_MAX, "title", "Title must be 5-140 characters.");
        v.check(body.length() <= BODY_MAX, "body", "Post text can be at most 5000 characters.");
        if (req.category() != null) {
            CommunityCategory cat = CommunityCategory.fromKey(req.category()).orElse(null);
            v.check(cat != null, "category", "Choose a valid topic.");
            if (cat != null) p.setCategory(cat.key());
        }
        v.throwIfInvalid();
        if (req.hazardReportId() != null) p.setHazardComplaintId(resolveHazard(req.hazardReportId(), v));
        p.setTitle(title);
        p.setBody(body);
        p.setUpdatedAt(Instant.now());
        posts.save(p);
        return detail(p, user, false);
    }

    /** Soft delete: the post disappears from lists; its comments stay as "[deleted]" so threads still make sense. */
    @Transactional
    public void deletePost(User user, Long id) {
        CommunityPost p = ownedPost(user, id);
        p.setDeleted(true);
        p.setUpdatedAt(Instant.now());
        posts.save(p);
    }

    /** Resolves a public report reference (e.g. RG-2026-00431) to the complaint id. Blank = no link; unknown = validation error. */
    private Long resolveHazard(String reportId, Validation v) {
        String ref = Inputs.clean(reportId);
        if (ref.isEmpty()) return null;
        var c = complaints.findByComplaintId(ref).filter(x -> !x.sample());
        if (c.isEmpty()) { v.fail("hazardReportId", "No hazard report has that reference number."); return null; }
        return c.get().getId();
    }

    /** The linked hazard with its live status, so the thread reflects what staff have actually recorded. */
    private LinkedHazard linked(CommunityPost p) {
        if (p.getHazardComplaintId() == null) return null;
        return complaints.findById(p.getHazardComplaintId()).filter(c -> !c.sample()).map(c -> new LinkedHazard(
                c.getComplaintId(), RoadIssueService.canonicalCategory(c.getIssueType()), c.getRoadName(),
                c.getStatus(), c.getStatus(), "/complaints/" + c.getId())).orElse(null);
    }

    private CommunityPost ownedPost(User user, Long id) {
        CommunityPost p = posts.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Post not found."));
        if (!p.getAuthor().getId().equals(user.getId())) throw ApiException.forbidden("You can only change your own posts.");
        return p;
    }

    /**
     * Lists posts. sort: new (newest first), hot (score weighted by age, Reddit-style), top (score, optionally within a period).
     * search matches title and body; category filters by topic; author limits to one user's posts (profile page).
     */
    @Transactional(readOnly = true)
    public PageView<PostSummary> list(User viewer, String category, String sort, String q, String period, Long authorId, int page, int size) {
        Specification<CommunityPost> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (category != null && !category.isBlank()) {
                CommunityCategory cat = CommunityCategory.fromKey(category).orElseThrow(() -> new ValidationException(Map.of("category", "Unknown topic.")));
                ps.add(cb.equal(root.get("category"), cat.key()));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'), cb.like(cb.lower(root.get("body")), like, '\\')));
            }
            if (authorId != null) ps.add(cb.equal(root.get("author").get("id"), authorId));
            if ("day".equals(period) || "week".equals(period) || "month".equals(period) || "year".equals(period)) {
                long days = "day".equals(period) ? 1 : "week".equals(period) ? 7 : "month".equals(period) ? 30 : 365;
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Instant.now().minus(Duration.ofDays(days))));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        String s = sort == null ? "hot" : sort.toLowerCase(Locale.ROOT);
        if (!List.of("new", "hot", "top").contains(s)) throw new ValidationException(Map.of("sort", "Sort must be new, hot or top."));
        int pageSize = Math.min(Math.max(size, 1), PAGE_MAX);
        int pageNo = Math.max(page, 0);

        // Hot is computed in Java (score with time decay), so it cannot be a simple column sort: fetch the candidates, rank, then page.
        List<CommunityPost> all = posts.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Instant now = Instant.now();
        Comparator<CommunityPost> order = switch (s) {
            case "new" -> Comparator.comparing(CommunityPost::getCreatedAt).reversed();
            case "top" -> Comparator.comparingInt(CommunityPost::getScore).reversed().thenComparing(CommunityPost::getCreatedAt, Comparator.reverseOrder());
            default -> Comparator.comparingDouble((CommunityPost p) -> hotScore(p, now)).reversed().thenComparing(CommunityPost::getCreatedAt, Comparator.reverseOrder());
        };
        all = new ArrayList<>(all);
        all.sort(order);
        int total = all.size();
        int from = Math.min(pageNo * pageSize, total), to = Math.min(from + pageSize, total);
        List<CommunityPost> pageItems = all.subList(from, to);
        Map<Long, Integer> mine = myVotesForPosts(viewer, pageItems);
        List<PostSummary> items = pageItems.stream().map(p -> summary(p, viewer, mine.getOrDefault(p.getId(), 0))).toList();
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        return new PageView<>(items, pageNo, pageSize, total, totalPages);
    }

    /** Reddit-style hot score: log of the magnitude of votes plus recency, so new posts with some support rise. */
    static double hotScore(CommunityPost p, Instant now) {
        int s = p.getScore();
        double order = Math.log10(Math.max(Math.abs(s), 1));
        int sign = s > 0 ? 1 : s < 0 ? -1 : 0;
        double seconds = p.getCreatedAt().getEpochSecond() - 1134028003L;   // seconds since an arbitrary epoch, as in Reddit's formula
        return sign * order + seconds / 45000.0;
    }

    @Transactional(readOnly = true)
    public PostDetail get(User viewer, Long id) {
        CommunityPost p = posts.findById(id).orElseThrow(() -> ApiException.notFound("Post not found."));
        if (p.isDeleted()) throw ApiException.notFound("This post was deleted by its author.");
        return detail(p, viewer, true);
    }

    private PostDetail detail(CommunityPost p, User viewer, boolean withComments) {
        int mine = viewer == null ? 0 : votes.findByPost_IdIn(List.of(p.getId())).stream()
                .filter(v -> v.getUser().getId().equals(viewer.getId())).mapToInt(CommunityVote::getValue).findFirst().orElse(0);
        List<CommentNode> tree = withComments ? commentTree(p, viewer) : List.of();
        CategoryLabel cl = CategoryLabel.of(p.getCategory());
        return new PostDetail(p.getId(), p.isDeleted() ? DELETED_TEXT : p.getTitle(), p.isDeleted() ? "" : p.getBody(), p.getCategory(), cl.label(),
                author(p.getAuthor(), viewer), p.getScore(), p.getUpCount(), p.getDownCount(), p.getCommentCount(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getUpdatedAt() != null, p.isDeleted(), mine,
                viewer != null && p.getAuthor().getId().equals(viewer.getId()), linked(p), tree);
    }

    private PostSummary summary(CommunityPost p, User viewer, int mine) {
        String body = p.getBody() == null ? "" : p.getBody();
        String preview = body.length() > 220 ? body.substring(0, 220).trim() + "…" : body;
        CategoryLabel cl = CategoryLabel.of(p.getCategory());
        return new PostSummary(p.getId(), p.getTitle(), preview, p.getCategory(), cl.label(), author(p.getAuthor(), viewer), p.getScore(),
                p.getCommentCount(), p.getCreatedAt(), p.getUpdatedAt(), p.getUpdatedAt() != null, mine,
                viewer != null && p.getAuthor().getId().equals(viewer.getId()));
    }

    private Map<Long, Integer> myVotesForPosts(User viewer, List<CommunityPost> items) {
        if (viewer == null || items.isEmpty()) return Map.of();
        List<Long> ids = items.stream().map(CommunityPost::getId).toList();
        return votes.findByPost_IdIn(ids).stream().filter(v -> v.getUser().getId().equals(viewer.getId()))
                .collect(Collectors.toMap(v -> v.getPost().getId(), CommunityVote::getValue, (a, b) -> a));
    }

    // ------------------------------------------------------------ votes

    /** vote: "up", "down" or "none" (to remove). Recomputes the stored score and counts from the vote rows. */
    @Transactional
    public VoteResult votePost(User user, Long id, String vote) {
        CommunityPost p = posts.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Post not found."));
        int value = parseVote(vote);
        if (!voteLimiter.tryAcquire("vote:" + user.getId())) throw ApiException.tooMany("You are voting too quickly. Please slow down a little.");
        var existing = votes.findByPost_IdAndUser_Id(id, user.getId());
        if (value == 0) existing.ifPresent(votes::delete);
        else if (existing.isPresent()) { existing.get().setValue(value); votes.save(existing.get()); }
        else votes.save(new CommunityVote(p, null, user, value));
        votes.flush();
        recountPost(p);
        return new VoteResult(p.getScore(), p.getUpCount(), p.getDownCount(), value);
    }

    @Transactional
    public VoteResult voteComment(User user, Long id, String vote) {
        CommunityComment c = comments.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Comment not found."));
        int value = parseVote(vote);
        if (!voteLimiter.tryAcquire("vote:" + user.getId())) throw ApiException.tooMany("You are voting too quickly. Please slow down a little.");
        var existing = votes.findByComment_IdAndUser_Id(id, user.getId());
        if (value == 0) existing.ifPresent(votes::delete);
        else if (existing.isPresent()) { existing.get().setValue(value); votes.save(existing.get()); }
        else votes.save(new CommunityVote(null, c, user, value));
        votes.flush();
        int score = votes.findByComment_IdIn(List.of(id)).stream().mapToInt(CommunityVote::getValue).sum();
        c.setScore(score);
        comments.save(c);
        return new VoteResult(score, 0, 0, value);
    }

    private static int parseVote(String vote) {
        return switch (vote == null ? "" : vote.trim().toLowerCase(Locale.ROOT)) {
            case "up" -> 1;
            case "down" -> -1;
            case "none" -> 0;
            default -> throw new ValidationException(Map.of("vote", "Vote must be up, down or none."));
        };
    }

    private void recountPost(CommunityPost p) {
        List<CommunityVote> rows = votes.findByPost_IdIn(List.of(p.getId()));
        int up = (int) rows.stream().filter(v -> v.getValue() > 0).count();
        int down = (int) rows.stream().filter(v -> v.getValue() < 0).count();
        p.setUpCount(up);
        p.setDownCount(down);
        p.setScore(up - down);
        posts.save(p);
    }

    // ------------------------------------------------------------ comments and replies

    @Transactional
    public CommentNode addComment(User user, Long postId, CommentRequest req) {
        if (!writeLimiter.tryAcquire("write:" + user.getId())) throw ApiException.tooMany("You are commenting too quickly. Please wait a little while.");
        CommunityPost p = posts.findById(postId).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Post not found."));
        String body = Inputs.clean(req.body());
        Validation v = new Validation();
        v.check(!body.isEmpty() && body.length() <= COMMENT_MAX, "body", "Write a comment (up to 2000 characters).");
        v.throwIfInvalid();
        CommunityComment parent = null;
        if (req.parentId() != null) {
            parent = comments.findById(req.parentId()).orElseThrow(() -> new ValidationException(Map.of("parentId", "The comment you are replying to no longer exists.")));
            if (!parent.getPost().getId().equals(postId)) throw new ValidationException(Map.of("parentId", "That comment belongs to a different post."));
        }
        CommunityComment c = comments.save(new CommunityComment(p, parent, user, body));
        p.setCommentCount(p.getCommentCount() + 1);
        posts.save(p);
        return node(c, user, List.of());
    }

    @Transactional
    public CommentNode editComment(User user, Long id, CommentRequest req) {
        CommunityComment c = ownedComment(user, id);
        String body = Inputs.clean(req.body());
        Validation v = new Validation();
        v.check(!body.isEmpty() && body.length() <= COMMENT_MAX, "body", "Write a comment (up to 2000 characters).");
        v.throwIfInvalid();
        c.setBody(body);
        c.setUpdatedAt(Instant.now());
        comments.save(c);
        return node(c, user, List.of());
    }

    /** Soft delete: the comment keeps its place in the thread (so replies stay attached) but its text is removed. */
    @Transactional
    public void deleteComment(User user, Long id) {
        CommunityComment c = ownedComment(user, id);
        c.setDeleted(true);
        c.setBody("");
        c.setUpdatedAt(Instant.now());
        comments.save(c);
    }

    private CommunityComment ownedComment(User user, Long id) {
        CommunityComment c = comments.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Comment not found."));
        if (!c.getAuthor().getId().equals(user.getId())) throw ApiException.forbidden("You can only change your own comments.");
        return c;
    }

    private List<CommentNode> commentTree(CommunityPost p, User viewer) {
        List<CommunityComment> all = comments.findByPost_IdOrderByCreatedAtAscIdAsc(p.getId());
        Map<Long, List<CommunityComment>> byParent = new HashMap<>();
        for (CommunityComment c : all) byParent.computeIfAbsent(c.getParent() == null ? 0L : c.getParent().getId(), k -> new ArrayList<>()).add(c);
        Map<Long, Integer> mine = new HashMap<>();
        if (viewer != null && !all.isEmpty()) {
            List<Long> ids = all.stream().map(CommunityComment::getId).toList();
            for (CommunityVote v : votes.findByComment_IdIn(ids)) {
                if (v.getUser().getId().equals(viewer.getId())) mine.put(v.getComment().getId(), v.getValue());
            }
        }
        return byParent.getOrDefault(0L, List.of()).stream().map(c -> build(c, byParent, mine, viewer, 0)).toList();
    }

    private CommentNode build(CommunityComment c, Map<Long, List<CommunityComment>> byParent, Map<Long, Integer> mine, User viewer, int depth) {
        List<CommentNode> replies = depth >= 12 ? List.of()
                : byParent.getOrDefault(c.getId(), List.of()).stream().map(r -> build(r, byParent, mine, viewer, depth + 1)).toList();
        CommentNode n = node(c, viewer, replies);
        return new CommentNode(n.id(), n.parentId(), n.body(), n.author(), n.score(), mine.getOrDefault(c.getId(), 0), n.mine(),
                n.deleted(), n.edited(), n.createdAt(), n.replies());
    }

    private CommentNode node(CommunityComment c, User viewer, List<CommentNode> replies) {
        boolean deleted = c.isDeleted();
        return new CommentNode(c.getId(), c.getParent() == null ? null : c.getParent().getId(),
                deleted ? DELETED_TEXT : c.getBody(), deleted ? null : author(c.getAuthor(), viewer), c.getScore(), 0,
                viewer != null && c.getAuthor().getId().equals(viewer.getId()) && !deleted, deleted, c.getUpdatedAt() != null,
                c.getCreatedAt(), replies);
    }

    // ------------------------------------------------------------ profiles

    @Transactional(readOnly = true)
    public ProfileView profile(User viewer, Long userId, int page, int size) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        long postCount = posts.countByAuthor_IdAndDeletedFalse(userId);
        long commentCount = comments.countByAuthor_IdAndDeletedFalse(userId);
        int postScore = posts.findAll().stream().filter(p -> !p.isDeleted() && p.getAuthor().getId().equals(userId)).mapToInt(CommunityPost::getScore).sum();
        PageView<PostSummary> recent = list(viewer, null, "new", null, null, userId, page, size);
        return new ProfileView(author(u, viewer), u.getCreatedAt(), postCount, commentCount, postScore, recent.items());
    }

    /** Public author card: first name and initial only, never e-mail or full name. */
    AuthorView author(User u, User viewer) {
        return new AuthorView(u.getId(), ImpactService.displayName(u), viewer != null && viewer.getId().equals(u.getId()));
    }

    /** Small helper so the category label lookup stays in one place. */
    private record CategoryLabel(String label) {
        static CategoryLabel of(String key) {
            return new CategoryLabel(CommunityCategory.fromKey(key).map(CommunityCategory::label).orElse(key));
        }
    }
}
