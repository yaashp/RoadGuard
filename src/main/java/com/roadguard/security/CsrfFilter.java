package com.roadguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF protection for the cookie-based session ("double-submit cookie" pattern).
 * <ul>
 *   <li>Every response sets a random XSRF-TOKEN cookie (readable by our own JavaScript) if the browser has none.</li>
 *   <li>Every state-changing /api request (POST, PUT, PATCH, DELETE) must repeat that value in the
 *       X-XSRF-TOKEN header. A malicious site can make the browser <em>send</em> the cookie but cannot
 *       <em>read</em> it, so it cannot build a matching header.</li>
 *   <li>The session cookie is additionally SameSite=Lax and HttpOnly (see application.properties).</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CsrfFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cookieToken = readCookie(request);

        boolean isApi = request.getRequestURI().startsWith("/api/");
        if (isApi && !SAFE_METHODS.contains(request.getMethod())) {
            String headerToken = request.getHeader(HEADER_NAME);
            if (!tokensMatch(cookieToken, headerToken)) {
                issueCookieIfMissing(request, response, cookieToken);
                reject(response);
                return;
            }
        }
        issueCookieIfMissing(request, response, cookieToken);
        chain.doFilter(request, response);
    }

    static boolean tokensMatch(String cookieToken, String headerToken) {
        if (cookieToken == null || cookieToken.isEmpty() || headerToken == null || headerToken.isEmpty()) return false;
        return MessageDigest.isEqual(cookieToken.getBytes(StandardCharsets.UTF_8), headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static void issueCookieIfMissing(HttpServletRequest request, HttpServletResponse response, String existing) {
        if (existing != null && !existing.isEmpty()) return;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        StringBuilder cookie = new StringBuilder(COOKIE_NAME).append('=').append(token)
                .append("; Path=/; SameSite=Lax");          // deliberately NOT HttpOnly: our JS must read it
        if (request.isSecure()) cookie.append("; Secure");
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"code\":\"CSRF_TOKEN_INVALID\","
                + "\"message\":\"Security token missing or invalid. Reload the page and try again.\"}");
    }
}
