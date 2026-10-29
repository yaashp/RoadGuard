package com.roadguard.geo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Small geometry toolbox. A "polyline" is a List of double[]{lat, lng}.
 * At city scale a local flat projection (metres east/north of a reference point) is accurate to
 * well under a metre, which is plenty for "is this pothole within 60 m of the route".
 */
public final class GeoUtils {
    public static final double EARTH_RADIUS_M = 6_371_008.8;
    private static final double METERS_PER_DEGREE = Math.PI * EARTH_RADIUS_M / 180.0;

    private GeoUtils() {}

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Shortest distance from point P to the segment A-B, in metres. */
    public static double distanceToSegmentMeters(double pLat, double pLng, double aLat, double aLng, double bLat, double bLng) {
        double cos = Math.cos(Math.toRadians(pLat));
        double ax = (aLng - pLng) * cos * METERS_PER_DEGREE, ay = (aLat - pLat) * METERS_PER_DEGREE;
        double bx = (bLng - pLng) * cos * METERS_PER_DEGREE, by = (bLat - pLat) * METERS_PER_DEGREE;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : -(ax * dx + ay * dy) / len2;   // projection of P (the origin) onto the segment
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    /** Shortest distance from a point to any segment of the polyline. Each hazard therefore gets ONE distance. */
    public static double distanceToPolylineMeters(double lat, double lng, List<double[]> line) {
        if (line.isEmpty()) return Double.POSITIVE_INFINITY;
        if (line.size() == 1) return haversineMeters(lat, lng, line.get(0)[0], line.get(0)[1]);
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < line.size() - 1; i++) {
            double[] a = line.get(i), b = line.get(i + 1);
            double d = distanceToSegmentMeters(lat, lng, a[0], a[1], b[0], b[1]);
            if (d < best) best = d;
        }
        return best;
    }

    public static double polylineLengthMeters(List<double[]> line) {
        double sum = 0;
        for (int i = 0; i < line.size() - 1; i++) {
            sum += haversineMeters(line.get(i)[0], line.get(i)[1], line.get(i + 1)[0], line.get(i + 1)[1]);
        }
        return sum;
    }

    /** {minLat, minLng, maxLat, maxLng} of the polyline, grown by bufferMeters on every side. */
    public static double[] boundingBox(List<double[]> line, double bufferMeters) {
        double minLat = Double.MAX_VALUE, minLng = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (double[] p : line) {
            minLat = Math.min(minLat, p[0]);
            maxLat = Math.max(maxLat, p[0]);
            minLng = Math.min(minLng, p[1]);
            maxLng = Math.max(maxLng, p[1]);
        }
        double dLat = bufferMeters / METERS_PER_DEGREE;
        double midLat = (minLat + maxLat) / 2;
        double dLng = bufferMeters / (METERS_PER_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(midLat))));
        return new double[]{minLat - dLat, minLng - dLng, maxLat + dLat, maxLng + dLng};
    }

    public static boolean inBox(double lat, double lng, double[] box) {
        return lat >= box[0] && lat <= box[2] && lng >= box[1] && lng <= box[3];
    }

    /** Douglas-Peucker simplification (iterative). Used only to shrink the geometry sent to the browser. */
    public static List<double[]> simplify(List<double[]> line, double toleranceMeters) {
        int n = line.size();
        if (n <= 2) return new ArrayList<>(line);
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int first = range[0], last = range[1];
            double maxDist = -1;
            int index = -1;
            for (int i = first + 1; i < last; i++) {
                double d = distanceToSegmentMeters(line.get(i)[0], line.get(i)[1],
                        line.get(first)[0], line.get(first)[1], line.get(last)[0], line.get(last)[1]);
                if (d > maxDist) {
                    maxDist = d;
                    index = i;
                }
            }
            if (maxDist > toleranceMeters && index > 0) {
                keep[index] = true;
                stack.push(new int[]{first, index});
                stack.push(new int[]{index, last});
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(line.get(i));
        return out;
    }
}
