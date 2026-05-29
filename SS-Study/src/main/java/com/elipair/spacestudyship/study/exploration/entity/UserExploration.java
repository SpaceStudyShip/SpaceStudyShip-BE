package com.elipair.spacestudyship.study.exploration.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_exploration_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_expl", columnNames = {"user_id", "node_id"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExploration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "node_id", nullable = false, length = 50)
    private String nodeId;

    @Column(name = "is_unlocked", nullable = false)
    private boolean isUnlocked;

    @Column(name = "is_cleared", nullable = false)
    private boolean isCleared;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    public static UserExploration unlock(Long userId, String nodeId, boolean cleared) {
        return UserExploration.builder()
                .userId(userId)
                .nodeId(nodeId)
                .isUnlocked(true)
                .isCleared(cleared)
                .unlockedAt(LocalDateTime.now())
                .build();
    }
}
