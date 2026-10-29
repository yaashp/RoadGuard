package com.roadguard.repository;

import com.roadguard.model.HazardVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HazardVerificationRepository extends JpaRepository<HazardVerification, Long> {
    List<HazardVerification> findByRoadIssue_Id(Long roadIssueId);
    Optional<HazardVerification> findByRoadIssue_IdAndUser_Id(Long roadIssueId, Long userId);
    List<HazardVerification> findByUser_Id(Long userId);
    long countByUser_IdAndCreatedAtAfter(Long userId, Instant since);
}
