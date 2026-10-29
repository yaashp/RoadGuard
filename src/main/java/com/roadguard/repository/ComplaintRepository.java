package com.roadguard.repository;

import com.roadguard.model.Complaint;
import com.roadguard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findByUserOrderByCreatedAtDesc(User user);
    List<Complaint> findAllByOrderByCreatedAtDesc();
    Optional<Complaint> findByComplaintId(String complaintId);
    Optional<Complaint> findByUser_IdAndClientRequestId(Long userId, String clientRequestId);
    long countByStatus(String status);
    long countByUser_Id(Long userId);
    long countByUser_IdAndStatus(Long userId, String status);
    List<Complaint> findByCreatedAtGreaterThanEqual(Instant since);
}
