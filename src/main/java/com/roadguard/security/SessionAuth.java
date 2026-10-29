package com.roadguard.security;

import com.roadguard.exception.ApiException;
import com.roadguard.model.User;
import com.roadguard.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Resolves "who is logged in" from the server-side HTTP session (the session id cookie is HttpOnly). */
@Component
public class SessionAuth {
    public static final String SESSION_USER_ID = "userId";
    private static final String REQUEST_ATTR = "roadguard.currentUser";

    private final UserRepository users;

    public SessionAuth(UserRepository users) {
        this.users = users;
    }

    /** The logged-in user, or null. Cached on the request so the database is hit at most once per request. */
    public User currentUserOrNull(HttpServletRequest request) {
        Object cached = request.getAttribute(REQUEST_ATTR);
        if (cached instanceof User u) return u;
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long userId)) return null;
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            session.invalidate();   // account was deleted: drop the stale session
            return null;
        }
        request.setAttribute(REQUEST_ATTR, user);
        return user;
    }

    public User requireUser(HttpServletRequest request) {
        User u = currentUserOrNull(request);
        if (u == null) throw ApiException.unauthorized("Please log in to continue.");
        return u;
    }

    public User requireAdmin(HttpServletRequest request) {
        User u = requireUser(request);
        if (!u.admin()) throw ApiException.forbidden("Administrator access only.");
        return u;
    }

    /** Starts a fresh session for this user (the old one is discarded to prevent session fixation). */
    public void startSession(HttpServletRequest request, User user) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true).setAttribute(SESSION_USER_ID, user.getId());
        request.setAttribute(REQUEST_ATTR, user);
    }

    public void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
