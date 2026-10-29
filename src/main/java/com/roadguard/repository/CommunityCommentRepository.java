package com.roadguard.repository;

import com.roadguard.model.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    List<CommunityComment> findByPost_IdOrderByCreatedAtAscIdAsc(Long postId);
    long countByAuthor_IdAndDeletedFalse(Long authorId);
}
