package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserExplorationRepository extends JpaRepository<UserExploration, Long> {

    List<UserExploration> findByUserId(Long userId);

    boolean existsByUserIdAndNodeId(Long userId, String nodeId);
}
