package com.roadguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.RouteDtos.GeocodeResult;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.ValidationException;
import com.roadguard.security.SlidingWindowCounter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Place search through a Nominatim-compatible service. To respect the public Nominatim usage policy this
 * service identifies itself with a User-Agent, caches answers, sends at most one upstream request per
 * second, and limits each client's request rate.
 */
@Service
public class GeocodingService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long CACHE_MILLIS = 10 * 60_000L;
    private static final long MIN_GAP_MILLIS = 1100;

    private record Cached(long expires, List<GeocodeResult> results) {}

    private final RoadGuardProperties.Geocoding cfg;
    private final String userAgent;
    private final HttpClient http;
    private final SlidingWindowCounter perClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Object upstreamLock = new Object();
    private long lastUpstreamCall = 0;

    public GeocodingService(RoadGuardProperties props) {
        this.cfg = props.getGeocoding();
        this.userAgent = props.getUserAgent();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfg.getTimeoutMs())).build();
        this.perClient = new SlidingWindowCounter(cfg.getRequestsPerMinutePerClient(), 60_000L);
    }

    public List<GeocodeResult> search(String query, String clientKey) {
        String q = Inputs.clean(query);
        if (q.length() < 3 || q.length() > 120) throw new ValidationException(Map.of("q", "Type 3 to 120 characters to search for a place."));
        if (!perClient.tryAcquire(clientKey)) throw ApiException.tooMany("You are searching too quickly. Please wait a few seconds.");

        String cacheKey = q.toLowerCase(Locale.ROOT);
        Cached hit = cache.get(cacheKey);
        if (hit != null && hit.expires() > System.currentTimeMillis()) return hit.results();

        List<GeocodeResult> results = fetch(q);
        if (cache.size() > 500) cache.clear();
        cache.put(cacheKey, new Cached(System.currentTimeMillis() + CACHE_MILLIS, results));
        return results;
    }

    private List<GeocodeResult> fetch(String q) {
        String base = cfg.getBaseUrl().replaceAll("/+$", "");
        StringBuilder url = new StringBuilder(base).append("/search?format=jsonv2&limit=6&addressdetails=0&q=")
                .append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        if (!cfg.getCountryCodes().isBlank()) url.append("&countrycodes=").append(URLEncoder.encode(cfg.getCountryCodes(), StandardCharsets.UTF_8));
        if (!cfg.getContactEmail().isBlank()) url.append("&email=").append(URLEncoder.encode(cfg.getContactEmail(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET().build();
        try {
            synchronized (upstreamLock) {   // one upstream request per second, as the usage policy asks
                long wait = lastUpstreamCall + MIN_GAP_MILLIS - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
                lastUpstreamCall = System.currentTimeMillis();
            }
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw ApiException.unavailable("GEOCODER_UNAVAILABLE", "The place-search service is not available right now.");
            }
            return parse(response.body());
        } catch (IOException e) {
            throw ApiException.unavailable("GEOCODER_UNAVAILABLE", "The place-search service could not be reached.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("GEOCODER_UNAVAILABLE", "The place search was interrupted.");
        }
    }

    /** Nominatim "jsonv2" search response: an array of objects with display_name, lat and lon (as strings). */
    static List<GeocodeResult> parse(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            List<GeocodeResult> out = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode n : root) {
                    double lat = Double.parseDouble(n.path("lat").asText());
                    double lng = Double.parseDouble(n.path("lon").asText());
                    if (!Inputs.validLatLng(lat, lng)) continue;
                    out.add(new GeocodeResult(n.path("display_name").asText(""), lat, lng, n.path("type").asText("")));
                }
            }
            return out;
        } catch (IOException | NumberFormatException e) {
            throw ApiException.unavailable("GEOCODER_UNAVAILABLE", "The place-search service returned an unreadable answer.");
        }
    }
}
