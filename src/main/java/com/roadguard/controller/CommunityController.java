package com.roadguard.controller;

import com.roadguard.dto.CommunityDtos.*;
import com.roadguard.model.User;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Road-safety community. Reading is public; writing needs a logged-in user, and edits/deletes are limited to the author in the service. */
@RestController
@RequestMapping("/api/community")
@Access(AccessLevel.PUBLIC)
public class CommunityController {
    private final CommunityService community;
    private final SessionAuth sessions;

    public CommunityController(CommunityService community, SessionAuth sessions) {
        this.community = community;
        this.sessions = sessions;
    }

    @GetMapping("/categories")
    public List<CategoryInfo> categories() { return community.categories(); }

    @GetMapping("/posts")
    public PageView<PostSummary> list(@RequestParam(value = "category", required = false) String category,
                                      @RequestParam(value = "sort", defaultValue = "hot") String sort,
                                      @RequestParam(value = "q", required = false) String q,
                                      @RequestParam(value = "period", required = false) String period,
                                      @RequestParam(value = "page", defaultValue = "0") int page,
                                      @RequestParam(value = "size", defaultValue = "20") int size,
                                      HttpServletRequest http) {
        return community.list(sessions.currentUserOrNull(http), category, sort, q, period, null, page, size);
    }

    @PostMapping("/posts")
    @Access(AccessLevel.USER)
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetail create(@RequestBody PostRequest req, HttpServletRequest http) {
        return community.createPost(sessions.requireUser(http), req);
    }

    @GetMapping("/posts/{id}")
    public PostDetail get(@PathVariable("id") Long id, HttpServletRequest http) {
        return community.get(sessions.currentUserOrNull(http), id);
    }

    @PutMapping("/posts/{id}")
    @Access(AccessLevel.USER)
    public PostDetail edit(@PathVariable("id") Long id, @RequestBody PostRequest req, HttpServletRequest http) {
        return community.editPost(sessions.requireUser(http), id, req);
    }

    @DeleteMapping("/posts/{id}")
    @Access(AccessLevel.USER)
    public java.util.Map<String, String> delete(@PathVariable("id") Long id, HttpServletRequest http) {
        community.deletePost(sessions.requireUser(http), id);
        return java.util.Map.of("status", "deleted");
    }

    @PostMapping("/posts/{id}/vote")
    @Access(AccessLevel.USER)
    public VoteResult votePost(@PathVariable("id") Long id, @RequestBody VoteRequest req, HttpServletRequest http) {
        return community.votePost(sessions.requireUser(http), id, req.vote());
    }

    @PostMapping("/posts/{id}/comments")
    @Access(AccessLevel.USER)
    @ResponseStatus(HttpStatus.CREATED)
    public CommentNode comment(@PathVariable("id") Long id, @RequestBody CommentRequest req, HttpServletRequest http) {
        return community.addComment(sessions.requireUser(http), id, req);
    }

    @PutMapping("/comments/{id}")
    @Access(AccessLevel.USER)
    public CommentNode editComment(@PathVariable("id") Long id, @RequestBody CommentRequest req, HttpServletRequest http) {
        return community.editComment(sessions.requireUser(http), id, req);
    }

    @DeleteMapping("/comments/{id}")
    @Access(AccessLevel.USER)
    public java.util.Map<String, String> deleteComment(@PathVariable("id") Long id, HttpServletRequest http) {
        community.deleteComment(sessions.requireUser(http), id);
        return java.util.Map.of("status", "deleted");
    }

    @PostMapping("/comments/{id}/vote")
    @Access(AccessLevel.USER)
    public VoteResult voteComment(@PathVariable("id") Long id, @RequestBody VoteRequest req, HttpServletRequest http) {
        return community.voteComment(sessions.requireUser(http), id, req.vote());
    }

    @GetMapping("/users/{id}")
    public ProfileView profile(@PathVariable("id") Long id,
                               @RequestParam(value = "page", defaultValue = "0") int page,
                               @RequestParam(value = "size", defaultValue = "20") int size,
                               HttpServletRequest http) {
        return community.profile(sessions.currentUserOrNull(http), id, page, size);
    }
}
