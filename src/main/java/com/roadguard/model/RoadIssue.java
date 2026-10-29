package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A pin on the public map / a hazard used by the route planner.
 * Pins come from three places: a citizen complaint (source REPORTED, linked by complaintRef),
 * an administrator adding one by hand (ADMIN), or the demo seeder (SAMPLE).
 */
@Entity
@Table(name = "road_issues", indexes = {
        @Index(name = "idx_road_issues_status", columnList = "status"),
        @Index(name = "idx_road_issues_complaint", columnList = "complaintRef")
})
public class RoadIssue {
    public static final String SOURCE_SAMPLE = "SAMPLE";
    public static final String SOURCE_REPORTED = "REPORTED";
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;
    private double lat;
    private double lng;
    private String roadName;
    private String severity;
    private String status;
    private int reportCount;

    // ---- columns added by the upgrade (nullable) ----
    /** SAMPLE / REPORTED / ADMIN. Rows with no value predate this column and are all demo data => SAMPLE. */
    private String source;
    /** Id of the complaint this pin was created from (REPORTED pins only). */
    private Long complaintRef;
    private Instant createdAt;
    private Instant updatedAt;
    /** An administrator reviewed and dismissed community flags up to this moment; only newer votes count towards a new flag. */
    private Instant flagClearedAt;

    public RoadIssue() {}

    public RoadIssue(String type, double lat, double lng, String roadName,
                     String severity, String status, int reportCount) {
        this.type = type;
        this.lat = lat;
        this.lng = lng;
        this.roadName = roadName;
        this.severity = severity;
        this.status = status;
        this.reportCount = reportCount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getComplaintRef() { return complaintRef; }
    public void setComplaintRef(Long complaintRef) { this.complaintRef = complaintRef; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getFlagClearedAt() { return flagClearedAt; }
    public void setFlagClearedAt(Instant v) { this.flagClearedAt = v; }

    public boolean sample() { return source == null || source.isBlank() || SOURCE_SAMPLE.equalsIgnoreCase(source); }
}
