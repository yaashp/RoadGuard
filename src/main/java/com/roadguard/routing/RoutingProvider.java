package com.roadguard.routing;

import java.util.List;

/**
 * Source of real road-network routes. Implement this interface to switch to another provider
 * (OpenRouteService, GraphHopper, Google Directions ...); the rest of the application only sees RouteCandidate.
 * Implementations that need an API key must read it from an environment variable, never from source code.
 */
public interface RoutingProvider {
    String name();

    /** Primary route first, then any alternatives. Throws RoutingUnavailableException / NoRouteException. */
    List<RouteCandidate> route(double fromLat, double fromLng, double toLat, double toLng);
}
