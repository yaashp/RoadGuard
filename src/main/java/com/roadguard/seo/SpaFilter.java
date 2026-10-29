package com.roadguard.seo;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Makes the single-page app reachable on real URLs (/map, /complaints/12 ...) so they can be indexed:
 * <ul>
 *   <li>serves the app shell for page requests, with the site URL, Search Console and Analytics values filled in;</li>
 *   <li>answers 404 (with a noindex tag) for unknown top-level paths;</li>
 *   <li>sends permanent 301 redirects for trailing slashes and for legacy URLs listed in roadguard.seo.redirects.</li>
 * </ul>
 * API, H2 console, error and static-file requests pass through untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SpaFilter extends OncePerRequestFilter {
    /** First path segments that belong to pages. Anything else is answered with 404. */
    static final Set<String> KNOWN_ROUTES = Set.of("", "login", "register", "report", "complaints", "dashboard",
            "impact", "community", "repairs", "repair", "planner", "saved", "history", "map", "admin",
            "notifications", "settings", "privacy", "terms", "contact");

    private final String siteUrl;
    private final String analyticsSnippet;
    private final String verificationTag;
    private final String shell;
    private final Map<String, String> redirects;

    public SpaFilter(@Value("${roadguard.seo.site-url}") String siteUrl,
                     @Value("${roadguard.seo.google-analytics-id:}") String analyticsId,
                     @Value("${roadguard.seo.search-console-verification:}") String verification,
                     @Value("${roadguard.seo.redirects:}") String redirectSpec) {
        this.siteUrl = trimSlash(siteUrl.trim());
        this.analyticsSnippet = analyticsSnippet(analyticsId.trim());
        this.verificationTag = verification.isBlank() ? ""
                : "<meta name=\"google-site-verification\" content=\"" + HtmlUtils.htmlEscape(verification.trim()) + "\">";
        this.redirects = parseRedirects(redirectSpec);
        try (var in = new ClassPathResource("static/index.html").getInputStream()) {
            this.shell = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (req.getDispatcherType() != DispatcherType.REQUEST || !isPageRequest(req, path)) {
            chain.doFilter(req, res);
            return;
        }
        String query = req.getQueryString() == null ? "" : "?" + req.getQueryString();
        if (path.equals("/index.html")) { redirect(res, "/" + query); return; }

        String clean = trimSlash(path);
        String legacy = redirects.get(clean);
        if (legacy != null) { redirect(res, legacy + query); return; }
        if (!clean.equals(path)) { redirect(res, clean + query); return; }

        boolean known = KNOWN_ROUTES.contains(firstSegment(clean));
        String html = shell
                .replace("__SITE_URL__", HtmlUtils.htmlEscape(siteUrl))
                .replace("<!--SEARCH_CONSOLE-->", verificationTag)
                .replace("<!--GA4-->", analyticsSnippet)
                .replace("<!--ROBOTS-->", known ? "" : "<meta name=\"robots\" content=\"noindex,nofollow\">");
        res.setStatus(known ? HttpServletResponse.SC_OK : HttpServletResponse.SC_NOT_FOUND);
        res.setContentType("text/html;charset=UTF-8");
        res.setHeader("Cache-Control", "no-cache");
        res.getWriter().write(html);
    }

    private static boolean isPageRequest(HttpServletRequest req, String path) {
        String method = req.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) return false;
        if (path.equals("/api") || path.startsWith("/api/") || path.startsWith("/h2-console") || path.startsWith("/error")) return false;
        String accept = req.getHeader("Accept");
        boolean wantsHtml = accept == null || accept.contains("text/html") || accept.contains("*/*");
        if (!wantsHtml) return false;
        return path.equals("/index.html") || !lastSegment(path).contains(".");
    }

    static String analyticsSnippet(String id) {
        if (!id.matches("G-[A-Z0-9]+|UA-[0-9]+-[0-9]+")) return "";   // only well-formed measurement IDs are injected
        return "<script async src=\"https://www.googletagmanager.com/gtag/js?id=" + id + "\"></script>\n"
                + "  <script>window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}"
                + "gtag('js',new Date());gtag('config','" + id + "',{send_page_view:false});</script>";
    }

    /** Parses "/old=/new;/other=/target" into a map keyed by the normalised old path. */
    static Map<String, String> parseRedirects(String spec) {
        Map<String, String> map = new HashMap<>();
        for (String pair : spec.split(";")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].trim().startsWith("/") && parts[1].trim().startsWith("/")) {
                map.put(trimSlash(parts[0].trim()), parts[1].trim());
            }
        }
        return map;
    }

    static String trimSlash(String s) {
        return s.length() > 1 && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String firstSegment(String path) {
        String s = path.startsWith("/") ? path.substring(1) : path;
        int i = s.indexOf('/');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void redirect(HttpServletResponse res, String location) {
        res.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        res.setHeader("Location", location);
    }
}
