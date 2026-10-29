package com.roadguard.repository;

import com.roadguard.model.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {
    List<ComplaintStatusHistory> findByComplaint_IdOrderByCreatedAtAscIdAsc(Long complaintId);
    boolean existsByComplaint_Id(Long complaintId);
    List<ComplaintStatusHistory> findTop15ByOrderByCreatedAtDescIdDesc();
    List<ComplaintStatusHistory> findTop15ByComplaint_User_IdOrderByCreatedAtDescIdDesc(Long userId);
    List<ComplaintStatusHistory> findByEventType(String eventType);
}
