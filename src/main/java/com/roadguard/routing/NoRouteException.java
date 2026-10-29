package com.roadguard.routing;

/** The routing service worked but found no drivable route between the two points. */
public class NoRouteException extends RuntimeException {
    public NoRouteException(String message) { super(message); }
}
