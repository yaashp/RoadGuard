package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "saved_routes", indexes = @Index(name = "idx_saved_routes_user", columnList = "user_id"))
public class SavedRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String originLabel;
    @Column(nullable = false)
    private double originLat;
    @Column(nullable = false)
    private double originLng;

    @Column(length = 200)
    private String destinationLabel;
    @Column(nullable = false)
    private double destinationLat;
    @Column(nullable = false)
    private double destinationLng;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ---- result of the most recent analysis (null until analysed) ----
    private Integer lastScore;
    private String lastClassification;
    private Integer lastHazardCount;
    private Double lastDistanceMeters;
    private Double lastDurationSeconds;
    private Instant lastAnalyzedAt;
    /** Score from the analysis before the latest one, so the UI can show the trend. */
    private Integer previousScore;

    public SavedRoute() {}

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOriginLabel() { return originLabel; }
    public void setOriginLabel(String v) { this.originLabel = v; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double v) { this.originLat = v; }
    public double getOriginLng() { return originLng; }
    public void setOriginLng(double v) { this.originLng = v; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String v) { this.destinationLabel = v; }
    public double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(double v) { this.destinationLat = v; }
    public double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(double v) { this.destinationLng = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getLastScore() { return lastScore; }
    public void setLastScore(Integer v) { this.lastScore = v; }
    public String getLastClassification() { return lastClassification; }
    public void setLastClassification(String v) { this.lastClassification = v; }
    public Integer getLastHazardCount() { return lastHazardCount; }
    public void setLastHazardCount(Integer v) { this.lastHazardCount = v; }
    public Double getLastDistanceMeters() { return lastDistanceMeters; }
    public void setLastDistanceMeters(Double v) { this.lastDistanceMeters = v; }
    public Double getLastDurationSeconds() { return lastDurationSeconds; }
    public void setLastDurationSeconds(Double v) { this.lastDurationSeconds = v; }
    public Instant getLastAnalyzedAt() { return lastAnalyzedAt; }
    public void setLastAnalyzedAt(Instant v) { this.lastAnalyzedAt = v; }
    public Integer getPreviousScore() { return previousScore; }
    public void setPreviousScore(Integer v) { this.previousScore = v; }
}
