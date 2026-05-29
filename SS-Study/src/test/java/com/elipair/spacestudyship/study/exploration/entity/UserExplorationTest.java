package com.elipair.spacestudyship.study.exploration.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserExplorationTest {

    @Test
    @DisplayName("unlock 팩토리: isUnlocked=true, unlockedAt 세팅, cleared 반영")
    void unlockFactory() {
        UserExploration region = UserExploration.unlock(1L, "japan", true);
        assertThat(region.getUserId()).isEqualTo(1L);
        assertThat(region.getNodeId()).isEqualTo("japan");
        assertThat(region.isUnlocked()).isTrue();
        assertThat(region.isCleared()).isTrue();
        assertThat(region.getUnlockedAt()).isNotNull();

        UserExploration planet = UserExploration.unlock(1L, "mars", false);
        assertThat(planet.isCleared()).isFalse();
    }
}
