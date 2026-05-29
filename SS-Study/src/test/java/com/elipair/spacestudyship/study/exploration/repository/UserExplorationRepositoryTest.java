package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class UserExplorationRepositoryTest {

    @Autowired
    UserExplorationRepository repository;

    @Test
    @DisplayName("findByUserId / existsByUserIdAndNodeId")
    void findAndExists() {
        repository.saveAndFlush(UserExploration.unlock(1L, "japan", true));

        assertThat(repository.findByUserId(1L)).hasSize(1);
        assertThat(repository.findByUserId(999L)).isEmpty();
        assertThat(repository.existsByUserIdAndNodeId(1L, "japan")).isTrue();
        assertThat(repository.existsByUserIdAndNodeId(1L, "mars")).isFalse();
    }

    @Test
    @DisplayName("UNIQUE(user_id, node_id) 위반 시 예외")
    void uniqueConstraint() {
        repository.saveAndFlush(UserExploration.unlock(1L, "mars", false));

        assertThatThrownBy(() ->
                repository.saveAndFlush(UserExploration.unlock(1L, "mars", false)))
                .isInstanceOf(Exception.class);
    }
}
