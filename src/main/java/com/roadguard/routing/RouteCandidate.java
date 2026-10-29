package com.roadguard.routing;

import java.util.List;

/** One route returned by a routing provider. geometry is a polyline of {lat, lng} points. */
public record RouteCandidate(List<double[]> geometry, double distanceMeters, double durationSeconds) {}
