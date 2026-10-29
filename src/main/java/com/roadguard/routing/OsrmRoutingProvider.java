package com.roadguard.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadguard.config.RoadGuardProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Talks to an OSRM-compatible HTTP API (GET /route/v1/{profile}/{lng,lat;lng,lat}).
 * The default base URL is the public OSRM demo server, which is meant for light use only;
 * set ROUTING_BASE_URL to your own OSRM instance for anything heavier. No API key is needed.
 */
@Component
public class OsrmRoutingProvider implements RoutingProvider {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RoadGuardProperties.Routing cfg;
    private final String userAgent;
    private final HttpClient http;

    public OsrmRoutingProvider(RoadGuardProperties props) {
        this.cfg = props.getRouting();
        this.userAgent = props.getUserAgent();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfg.getTimeoutMs())).build();
    }

    @Override
    public String name() { return "OSRM (" + cfg.getBaseUrl() + ")"; }

    @Override
    public List<RouteCandidate> route(double fromLat, double fromLng, double toLat, double toLng) {
        String base = cfg.getBaseUrl().replaceAll("/+$", "");
        String coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", fromLng, fromLat, toLng, toLat);
        String alternatives = cfg.getMaxAlternatives() > 1 ? String.valueOf(cfg.getMaxAlternatives()) : (cfg.getMaxAlternatives() == 1 ? "true" : "false");
        URI uri = URI.create(base + "/route/v1/" + cfg.getProfile() + "/" + coords
                + "?overview=full&geometries=geojson&steps=false&alternatives=" + alternatives);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // OSRM answers 400 with {"code":"NoRoute"} for unreachable points, so look at the body before the status
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new RoutingUnavailableException("The routing service answered with HTTP " + response.statusCode() + ".");
            }
            return parse(response.body());
        } catch (IOException e) {
            throw new RoutingUnavailableException("The routing service could not be reached.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailableException("The routing request was interrupted.", e);
        }
    }

    /** Parses an OSRM route response. GeoJSON coordinates are [lng, lat]; we flip them to {lat, lng}. */
    static List<RouteCandidate> parse(String body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw new RoutingUnavailableException("The routing service returned an unreadable response.", e);
        }
        String code = root == null ? "" : root.path("code").asText("");
        if ("NoRoute".equals(code) || "NoSegment".equals(code)) {
            throw new NoRouteException("No drivable route was found between these two locations.");
        }
        if (!"Ok".equals(code)) {
            throw new RoutingUnavailableException("The routing service reported \"" + code + "\".");
        }
        List<RouteCandidate> out = new ArrayList<>();
        for (JsonNode route : root.path("routes")) {
            List<double[]> line = new ArrayList<>();
            for (JsonNode pt : route.path("geometry").path("coordinates")) {
                if (pt.size() >= 2) line.add(new double[]{pt.get(1).asDouble(), pt.get(0).asDouble()});
            }
            if (line.size() < 2) continue;
            out.add(new RouteCandidate(line, route.path("distance").asDouble(0), route.path("duration").asDouble(0)));
        }
        if (out.isEmpty()) throw new NoRouteException("No drivable route was found between these two locations.");
        return out;
    }
}
