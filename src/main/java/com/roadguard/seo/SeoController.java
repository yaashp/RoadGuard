package com.roadguard.seo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** robots.txt and sitemap.xml, generated from the configured site URL. */
@RestController
public class SeoController {
    /** Public pages worth indexing. Login-only and personal pages are deliberately left out. */
    static final List<String> PUBLIC_PAGES = List.of("/", "/map", "/community", "/repairs", "/impact", "/history",
            "/contact", "/privacy", "/terms");

    private final String siteUrl;

    public SeoController(@Value("${roadguard.seo.site-url}") String siteUrl) {
        this.siteUrl = SpaFilter.trimSlash(siteUrl.trim());
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public String robots() {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /api/\n"
                + "Disallow: /admin\n"
                + "Disallow: /complaints\n"
                + "Disallow: /dashboard\n"
                + "Disallow: /notifications\n"
                + "Disallow: /planner\n"
                + "Disallow: /saved\n"
                + "Disallow: /settings\n"
                + "Disallow: /h2-console\n"
                + "\n"
                + "Sitemap: " + siteUrl + "/sitemap.xml\n";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String page : PUBLIC_PAGES) {
            xml.append("  <url><loc>").append(escapeXml(siteUrl + page))
               .append("</loc><lastmod>").append(today).append("</lastmod></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
