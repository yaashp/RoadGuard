package com.roadguard.repository;

import com.roadguard.model.SavedRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long> {
    List<SavedRoute> findByUser_IdOrderByCreatedAtDesc(Long userId);
    /** Ownership is part of the query: another user's id simply finds nothing. */
    Optional<SavedRoute> findByIdAndUser_Id(Long id, Long userId);
    long countByUser_Id(Long userId);
}
