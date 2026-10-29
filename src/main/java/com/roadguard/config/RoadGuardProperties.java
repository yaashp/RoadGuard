package com.roadguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All RoadGuard-specific settings live under the "roadguard." prefix in application.properties.
 * Nothing in here is a secret: the integrations used (OpenStreetMap tiles, OSRM routing,
 * Nominatim geocoding) need no API key. If you point them at a provider that does need one,
 * read it from an environment variable (see .env.example) and never commit it.
 */
@ConfigurationProperties(prefix = "roadguard")
public class RoadGuardProperties {

    private final MapSettings map = new MapSettings();
    private final Routing routing = new Routing();
    private final Geocoding geocoding = new Geocoding();
    private final Scoring scoring = new Scoring();
    private final Upload upload = new Upload();
    private final Seed seed = new Seed();
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private final Verification verification = new Verification();
    /** Time zone used to bucket charts into weeks and months. */
    private String timeZone = "Asia/Kolkata";
    /** Identifies this app to OpenStreetMap-run services (Nominatim, OSRM demo), as their usage policies require. */
    private String userAgent = "RoadGuard-CollegeProject/1.0";

    public MapSettings getMap() { return map; }
    public Routing getRouting() { return routing; }
    public Geocoding getGeocoding() { return geocoding; }
    public Scoring getScoring() { return scoring; }
    public Upload getUpload() { return upload; }
    public Seed getSeed() { return seed; }
    public BootstrapAdmin getBootstrapAdmin() { return bootstrapAdmin; }
    public Verification getVerification() { return verification; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    /** Tile provider the browser map uses. Any XYZ raster tile service works. */
    public static class MapSettings {
        private String tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
        private String attribution = "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";
        private int maxZoom = 19;
        private double centerLat = 19.0760;
        private double centerLng = 72.8777;
        private int zoom = 11;

        public String getTileUrl() { return tileUrl; }
        public void setTileUrl(String tileUrl) { this.tileUrl = tileUrl; }
        public String getAttribution() { return attribution; }
        public void setAttribution(String attribution) { this.attribution = attribution; }
        public int getMaxZoom() { return maxZoom; }
        public void setMaxZoom(int maxZoom) { this.maxZoom = maxZoom; }
        public double getCenterLat() { return centerLat; }
        public void setCenterLat(double centerLat) { this.centerLat = centerLat; }
        public double getCenterLng() { return centerLng; }
        public void setCenterLng(double centerLng) { this.centerLng = centerLng; }
        public int getZoom() { return zoom; }
        public void setZoom(int zoom) { this.zoom = zoom; }
    }

    /** OSRM-compatible routing service (the public demo server by default; self-host for real use). */
    public static class Routing {
        private String baseUrl = "https://router.project-osrm.org";
        private String profile = "driving";
        private int timeoutMs = 8000;
        private int maxAlternatives = 3;
        private double maxDistanceKm = 600;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxAlternatives() { return maxAlternatives; }
        public void setMaxAlternatives(int maxAlternatives) { this.maxAlternatives = maxAlternatives; }
        public double getMaxDistanceKm() { return maxDistanceKm; }
        public void setMaxDistanceKm(double maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }
    }

    /** Nominatim-compatible place search, used by the route planner and map search box. */
    public static class Geocoding {
        private String baseUrl = "https://nominatim.openstreetmap.org";
        private String contactEmail = "";
        private String countryCodes = "in";
        private int timeoutMs = 6000;
        private int requestsPerMinutePerClient = 20;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
        public String getCountryCodes() { return countryCodes; }
        public void setCountryCodes(String countryCodes) { this.countryCodes = countryCodes; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getRequestsPerMinutePerClient() { return requestsPerMinutePerClient; }
        public void setRequestsPerMinutePerClient(int v) { this.requestsPerMinutePerClient = v; }
    }

    /**
     * Route safety scoring model (see RouteSafetyService for the full description).
     * score = 100 - sum(penalty of every distinct hazard near the route), clamped to 0..100.
     * penalty = severityPenalty x proximityFactor x statusFactor.
     */
    public static class Scoring {
        private double lowPenalty = 2.0;
        private double mediumPenalty = 5.0;
        private double highPenalty = 10.0;
        /** Hazards this close to the route line count at full weight. */
        private double nearMeters = 20;
        /** Hazards farther than this from the route line are ignored. */
        private double bufferMeters = 60;
        /** Weight at the outer edge of the buffer (linear fall-off from nearMeters). */
        private double edgeFactor = 0.5;
        /** An "In Progress" hazard is already being fixed, so it counts a bit less. */
        private double inProgressFactor = 0.75;
        /** Reports of the same category closer than this are treated as one hazard. */
        private double duplicateMeters = 15;
        /** Reports up to this old count fully; older ones fade linearly to recencyMinFactor at recencyMinDays. */
        private double recencyFullDays = 30;
        private double recencyMinDays = 180;
        private double recencyMinFactor = 0.6;
        /** Community confidence: each "still there" vote adds, each "no longer there" vote subtracts (both capped). */
        private double confirmBonusPerVote = 0.1;
        private double confirmMaxBonus = 0.3;
        private double goneMalusPerVote = 0.15;
        private double goneMaxMalus = 0.5;
        private int safeMinScore = 80;
        private int moderateMinScore = 50;

        public double getLowPenalty() { return lowPenalty; }
        public void setLowPenalty(double v) { this.lowPenalty = v; }
        public double getMediumPenalty() { return mediumPenalty; }
        public void setMediumPenalty(double v) { this.mediumPenalty = v; }
        public double getHighPenalty() { return highPenalty; }
        public void setHighPenalty(double v) { this.highPenalty = v; }
        public double getNearMeters() { return nearMeters; }
        public void setNearMeters(double v) { this.nearMeters = v; }
        public double getBufferMeters() { return bufferMeters; }
        public void setBufferMeters(double v) { this.bufferMeters = v; }
        public double getEdgeFactor() { return edgeFactor; }
        public void setEdgeFactor(double v) { this.edgeFactor = v; }
        public double getInProgressFactor() { return inProgressFactor; }
        public void setInProgressFactor(double v) { this.inProgressFactor = v; }
        public double getDuplicateMeters() { return duplicateMeters; }
        public void setDuplicateMeters(double v) { this.duplicateMeters = v; }
        public double getRecencyFullDays() { return recencyFullDays; }
        public void setRecencyFullDays(double v) { this.recencyFullDays = v; }
        public double getRecencyMinDays() { return recencyMinDays; }
        public void setRecencyMinDays(double v) { this.recencyMinDays = v; }
        public double getRecencyMinFactor() { return recencyMinFactor; }
        public void setRecencyMinFactor(double v) { this.recencyMinFactor = v; }
        public double getConfirmBonusPerVote() { return confirmBonusPerVote; }
        public void setConfirmBonusPerVote(double v) { this.confirmBonusPerVote = v; }
        public double getConfirmMaxBonus() { return confirmMaxBonus; }
        public void setConfirmMaxBonus(double v) { this.confirmMaxBonus = v; }
        public double getGoneMalusPerVote() { return goneMalusPerVote; }
        public void setGoneMalusPerVote(double v) { this.goneMalusPerVote = v; }
        public double getGoneMaxMalus() { return goneMaxMalus; }
        public void setGoneMaxMalus(double v) { this.goneMaxMalus = v; }
        public int getSafeMinScore() { return safeMinScore; }
        public void setSafeMinScore(int v) { this.safeMinScore = v; }
        public int getModerateMinScore() { return moderateMinScore; }
        public void setModerateMinScore(int v) { this.moderateMinScore = v; }
    }

    /** Where complaint photos are stored on disk (never inside the web root). */
    public static class Upload {
        private String dir = "./data/uploads";
        private long maxBytes = 5L * 1024 * 1024;
        private int maxPixels = 25_000_000;

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
        public int getMaxPixels() { return maxPixels; }
        public void setMaxPixels(int maxPixels) { this.maxPixels = maxPixels; }
    }

    /** Duplicate detection, community verification and abuse limits. */
    public static class Verification {
        private double duplicateRadiusMeters = 40;
        private int duplicateMaxAgeDays = 60;
        /** This many independent opinions raise a flag for administrators (they never change an official status). */
        private int flagThreshold = 3;
        private int maxVotesPerHour = 30;
        private int maxReportsPerHour = 20;

        public double getDuplicateRadiusMeters() { return duplicateRadiusMeters; }
        public void setDuplicateRadiusMeters(double v) { this.duplicateRadiusMeters = v; }
        public int getDuplicateMaxAgeDays() { return duplicateMaxAgeDays; }
        public void setDuplicateMaxAgeDays(int v) { this.duplicateMaxAgeDays = v; }
        public int getFlagThreshold() { return flagThreshold; }
        public void setFlagThreshold(int v) { this.flagThreshold = v; }
        public int getMaxVotesPerHour() { return maxVotesPerHour; }
        public void setMaxVotesPerHour(int v) { this.maxVotesPerHour = v; }
        public int getMaxReportsPerHour() { return maxReportsPerHour; }
        public void setMaxReportsPerHour(int v) { this.maxReportsPerHour = v; }
    }

    /**
     * Optional first administrator for a real deployment (no demo data needed). Both values must be set; the account is created
     * only if no user with that e-mail exists, and an existing account is never changed.
     */
    public static class BootstrapAdmin {
        private String email = "";
        private String password = "";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email == null ? "" : email.trim(); }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password == null ? "" : password; }
    }

    /** Demo accounts and sample records. Everything seeded is flagged SAMPLE in the database and the UI. */
    public static class Seed {
        private boolean enabled = true;
        private String demoUserEmail = "demo@roadguard.app";
        private String demoUserPassword = "password123";
        private String adminEmail = "admin@roadguard.app";
        private String adminPassword = "admin123";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDemoUserEmail() { return demoUserEmail; }
        public void setDemoUserEmail(String v) { this.demoUserEmail = v; }
        public String getDemoUserPassword() { return demoUserPassword; }
        public void setDemoUserPassword(String v) { this.demoUserPassword = v; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String v) { this.adminEmail = v; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String v) { this.adminPassword = v; }
    }
}
