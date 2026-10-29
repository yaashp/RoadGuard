package com.roadguard.routing;

/** The routing service could not be reached, timed out or answered with something unusable. */
public class RoutingUnavailableException extends RuntimeException {
    public RoutingUnavailableException(String message) { super(message); }
    public RoutingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
