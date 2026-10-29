package com.roadguard.repository;

import com.roadguard.model.CommunityVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityVoteRepository extends JpaRepository<CommunityVote, Long> {
    Optional<CommunityVote> findByPost_IdAndUser_Id(Long postId, Long userId);
    Optional<CommunityVote> findByComment_IdAndUser_Id(Long commentId, Long userId);
    List<CommunityVote> findByPost_IdIn(List<Long> postIds);
    List<CommunityVote> findByComment_IdIn(List<Long> commentIds);
    List<CommunityVote> findByUser_IdAndPostIsNotNull(Long userId);
}
