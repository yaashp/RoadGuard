package com.roadguard.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_status", columnList = "status"),
        @Index(name = "idx_complaints_created", columnList = "createdAt"),
        @Index(name = "idx_complaints_user", columnList = "user_id"),
        @Index(name = "idx_complaints_client_request", columnList = "clientRequestId")
})
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-friendly id such as RG-2026-00417 */
    @Column(unique = true)
    private String complaintId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String issueType;

    @Column(length = 2000)
    private String description;

    /** Legacy: photo stored as a base64 data URL. New photos go to disk (imagePath). */
    @Lob
    private String image;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private String address;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status = "Submitted";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ---- columns added by the upgrade: all nullable so existing rows stay valid ----

    /** Road / street name used to group history (falls back to the first part of the address). */
    private String roadName;

    /** Stored file name (inside the configured upload directory), never a client-supplied name. */
    private String imagePath;

    private String imageContentType;

    private Instant updatedAt;

    /** "SAMPLE" for demo rows inserted by the seeder, "USER" (or null) for real reports. */
    private String dataOrigin;

    /** Random key made by the browser for one submission, so a retried upload can never create a second report. */
    @Column(length = 64)
    private String clientRequestId;

    /** Set when an administrator marks this report as a duplicate of another one. */
    private Long duplicateOfId;

    /** Optional "after" photo uploaded by an administrator as repair evidence. */
    private String resolutionImagePath;
    private String resolutionImageContentType;

    /** Authority loop: the ward or department the citizen believes is responsible (free text, optional). */
    @Column(length = 120)
    private String authorityName;
    /** Staff-entered response from that authority, and when it was recorded. Never written by citizens. */
    @Column(length = 1000)
    private String authorityResponse;
    private Instant authorityResponseAt;
    /** Ticks when a citizen downloads their letter, so staff can see the report was taken forward. */
    private Instant letterExportedAt;

    public String getAuthorityName() { return authorityName; }
    public void setAuthorityName(String v) { this.authorityName = v; }
    public String getAuthorityResponse() { return authorityResponse; }
    public void setAuthorityResponse(String v) { this.authorityResponse = v; }
    public Instant getAuthorityResponseAt() { return authorityResponseAt; }
    public void setAuthorityResponseAt(Instant v) { this.authorityResponseAt = v; }
    public Instant getLetterExportedAt() { return letterExportedAt; }
    public void setLetterExportedAt(Instant v) { this.letterExportedAt = v; }

    public Complaint() {}

    public Long getId() { return id; }
    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getImageContentType() { return imageContentType; }
    public void setImageContentType(String imageContentType) { this.imageContentType = imageContentType; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getDataOrigin() { return dataOrigin; }
    public void setDataOrigin(String dataOrigin) { this.dataOrigin = dataOrigin; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String v) { this.clientRequestId = v; }
    public Long getDuplicateOfId() { return duplicateOfId; }
    public void setDuplicateOfId(Long v) { this.duplicateOfId = v; }
    public String getResolutionImagePath() { return resolutionImagePath; }
    public void setResolutionImagePath(String v) { this.resolutionImagePath = v; }
    public String getResolutionImageContentType() { return resolutionImageContentType; }
    public void setResolutionImageContentType(String v) { this.resolutionImageContentType = v; }

    public boolean sample() { return DataOrigin.isSample(dataOrigin, DataOrigin.USER); }

    public boolean hasImage() {
        return (imagePath != null && !imagePath.isBlank()) || (image != null && image.startsWith("data:image/"));
    }
}
