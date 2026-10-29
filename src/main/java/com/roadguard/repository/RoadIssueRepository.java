package com.roadguard.repository;

import com.roadguard.model.RoadIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadIssueRepository extends JpaRepository<RoadIssue, Long> {
    Optional<RoadIssue> findFirstByComplaintRef(Long complaintRef);
}
