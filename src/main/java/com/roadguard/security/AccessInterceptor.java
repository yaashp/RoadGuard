package com.roadguard.security;

import com.roadguard.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Server-side authorization for every /api endpoint. Hiding a button in the UI is not security;
 * this is: a normal user who hand-crafts a request to an admin URL gets 403 here, before any
 * controller code runs.
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AccessInterceptor.class);

    private final SessionAuth auth;

    public AccessInterceptor(SessionAuth auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;   // static files, error pages
        AccessLevel level = levelFor(method);
        if (level == null) {
            log.error("Endpoint {} {} has no @Access rule - refusing request", request.getMethod(), request.getRequestURI());
            throw ApiException.forbidden("This endpoint has no access rule configured.");
        }
        switch (level) {
            case PUBLIC -> { /* anyone */ }
            case USER -> auth.requireUser(request);
            case ADMIN -> auth.requireAdmin(request);
        }
        return true;
    }

    static AccessLevel levelFor(HandlerMethod method) {
        Access onMethod = method.getMethodAnnotation(Access.class);
        if (onMethod != null) return onMethod.value();
        Access onClass = method.getBeanType().getAnnotation(Access.class);
        return onClass == null ? null : onClass.value();
    }
}
