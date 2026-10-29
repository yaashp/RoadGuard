package com.roadguard.repository;

import com.roadguard.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Page<Notification> findByUser_IdAndReadFlagFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    long countByUser_IdAndReadFlagFalse(Long userId);
    List<Notification> findByUser_IdAndReadFlagFalse(Long userId);
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);
}
