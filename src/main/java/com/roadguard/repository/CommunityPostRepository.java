package com.roadguard.repository;

import com.roadguard.model.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, JpaSpecificationExecutor<CommunityPost> {
    Page<CommunityPost> findByAuthor_IdAndDeletedFalse(Long authorId, Pageable pageable);
    long countByAuthor_IdAndDeletedFalse(Long authorId);

    /** Posts created since a moment, for "Top this week" style counts. */
    @Query("select p from CommunityPost p where p.deleted = false and p.createdAt >= :since")
    List<CommunityPost> recentSince(@Param("since") Instant since);
}
