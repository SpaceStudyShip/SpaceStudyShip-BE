package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class FuelTransactionRepositoryTest {

    @Autowired
    FuelTransactionRepository transactionRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("findByFilters: type/날짜 모두 null이면 user의 모든 거래, createdAt DESC")
    void findByFilters_noFilter() throws InterruptedException {
        save("t1", 1L, TransactionType.CHARGE, 100, FuelReason.STUDY_SESSION, "s1", 100);
        Thread.sleep(5);
        save("t2", 1L, TransactionType.CONSUME, 30, FuelReason.EXPLORATION_UNLOCK, "r1", 70);
        save("t3", 2L, TransactionType.CHARGE, 50, FuelReason.STUDY_SESSION, "s2", 50);

        Page<FuelTransaction> page = transactionRepository.findByFilters(
                1L, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getId()).isEqualTo("t2");
        assertThat(page.getContent().get(1).getId()).isEqualTo("t1");
    }

    @Test
    @DisplayName("findByFilters: type=CHARGE 필터")
    void findByFilters_typeCharge() {
        save("t1", 1L, TransactionType.CHARGE, 100, FuelReason.STUDY_SESSION, "s1", 100);
        save("t2", 1L, TransactionType.CONSUME, 30, FuelReason.EXPLORATION_UNLOCK, "r1", 70);

        Page<FuelTransaction> page = transactionRepository.findByFilters(
                1L, TransactionType.CHARGE, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo("t1");
    }

    @Test
    @DisplayName("findByFilters: 날짜 범위 [start, end) 검증")
    void findByFilters_dateRange() {
        LocalDateTime today = LocalDateTime.now();
        save("t1", 1L, TransactionType.CHARGE, 100, FuelReason.STUDY_SESSION, "s1", 100);

        Page<FuelTransaction> in = transactionRepository.findByFilters(
                1L, null, today.minusDays(1), today.plusDays(1),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(in.getContent()).hasSize(1);

        Page<FuelTransaction> out = transactionRepository.findByFilters(
                1L, null, today.plusDays(2), today.plusDays(3),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(out.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findByFilters: 페이지네이션 동작 - size=2, page=0/1")
    void findByFilters_pagination() throws InterruptedException {
        for (int i = 1; i <= 5; i++) {
            save("t" + i, 1L, TransactionType.CHARGE, 10, FuelReason.STUDY_SESSION, "s" + i, 10);
            Thread.sleep(2);
        }

        Page<FuelTransaction> p0 = transactionRepository.findByFilters(
                1L, null, null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<FuelTransaction> p1 = transactionRepository.findByFilters(
                1L, null, null, null,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(p0.getTotalElements()).isEqualTo(5);
        assertThat(p0.getTotalPages()).isEqualTo(3);
        assertThat(p0.getContent()).hasSize(2);
        assertThat(p1.getContent()).hasSize(2);
        assertThat(p0.getContent().get(0).getId()).isNotEqualTo(p1.getContent().get(0).getId());
    }

    @Test
    @DisplayName("CHECK 제약: amount=0 native insert 시 실패 (ddl-auto=create-drop + @Check 의존)")
    void checkConstraint_amountPositive() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("""
                INSERT INTO fuel_transactions
                    (id, user_id, type, amount, reason, balance_after, created_at, updated_at)
                VALUES ('tx-zero', 1, 'CHARGE', 0, 'STUDY_SESSION', 0, NOW(), NOW())
                """).executeUpdate();
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    private void save(String id, Long userId, TransactionType type, int amount,
                      FuelReason reason, String refId, int balanceAfter) {
        transactionRepository.saveAndFlush(FuelTransaction.of(id, userId, type, amount, reason, refId, balanceAfter));
    }
}
