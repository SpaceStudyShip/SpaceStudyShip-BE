package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class UserFuelRepositoryTest {

    @Autowired
    UserFuelRepository userFuelRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("findByUserId: 초기화된 UserFuel 조회")
    void findByUserId_returnsExisting() {
        userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThat(userFuelRepository.findByUserId(1L)).isPresent();
        assertThat(userFuelRepository.findByUserId(999L)).isNotPresent();
    }

    @Test
    @DisplayName("existsByUserId: 존재 여부 boolean 반환")
    void existsByUserId_basic() {
        userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThat(userFuelRepository.existsByUserId(1L)).isTrue();
        assertThat(userFuelRepository.existsByUserId(999L)).isFalse();
    }

    @Test
    @DisplayName("findByUserIdForUpdate: 락 획득 후 row 반환 (smoke - 실제 락 경합은 통합 테스트 범위)")
    void findByUserIdForUpdate_returnsRow() {
        userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThat(userFuelRepository.findByUserIdForUpdate(1L)).isPresent();
    }

    @Test
    @DisplayName("current_fuel을 음수로 update 시 CHECK 제약으로 실패 (ddl-auto=create-drop + @Check 의존)")
    void checkConstraint_currentFuelNonNegative() {
        UserFuel saved = userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThatThrownBy(() -> {
            em.createNativeQuery("UPDATE user_fuel SET current_fuel = -1 WHERE user_id = :uid")
                    .setParameter("uid", saved.getUserId())
                    .executeUpdate();
            em.flush();
        }).isInstanceOf(Exception.class);
    }
}
