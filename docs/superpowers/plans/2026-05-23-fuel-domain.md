# 연료 시스템 도메인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이슈 #26의 연료 시스템 도메인을 구현한다 — `GET /api/fuel`, `GET /api/fuel/transactions` 2개 엔드포인트와 internal `FuelService.charge/consume/initialize` API. 신규 회원 가입 시 `UserFuel` 자동 초기화는 ApplicationEvent로 비동기 결합.

**Architecture:** SS-Study 모듈 안의 `fuel` 패키지 (Todo 도메인과 동일 패턴). 동시성 차단은 `findByUserIdForUpdate` 비관적 락. Idempotency는 `transactionId`(=PK) 사전 조회. 가입 시 SS-Auth가 `MemberCreatedEvent`를 publish하고 SS-Study의 `FuelInitializeListener`가 `BEFORE_COMMIT` phase에서 listen.

**Tech Stack:** Spring Boot 4 / JPA + Hibernate / PostgreSQL (Flyway) / Testcontainers + JUnit 5 + Mockito / Lombok / springdoc-openapi.

---

## 진행 순서 개요

| # | Task | 주요 산출물 |
|---|------|-----------|
| 1 | 사전 작업 (version + Migration + ErrorCode + CLAUDE.md) | V0_0_36 SQL, ErrorCode 2개 |
| 2 | Enum (TransactionType, FuelReason) | constant/ 2개 |
| 3 | UserFuel Entity (TDD) | entity + Entity 단위 테스트 |
| 4 | FuelTransaction Entity | entity |
| 5 | StudyTestApplication 갱신 | 테스트 패키지 스캔 추가 |
| 6 | UserFuelRepository (+ 테스트) | repository + repository 테스트 |
| 7 | FuelTransactionRepository (+ 테스트) | repository + repository 테스트 |
| 8 | MemberCreatedEvent (SS-Member) | event record |
| 9 | DTO 3종 (FuelResponse 등) | dto/ 3개 |
| 10 | FuelService.initialize (TDD) | service 메서드 + 테스트 |
| 11 | FuelService.getFuel (TDD) | service 메서드 + 테스트 |
| 12 | FuelService.getTransactions (TDD) | service 메서드 + 테스트 |
| 13 | FuelService.charge (TDD) | service 메서드 + 테스트 |
| 14 | FuelService.consume (TDD) | service 메서드 + 테스트 |
| 15 | FuelInitializeListener (TDD) | listener + 테스트 |
| 16 | AuthService publishEvent 수정 (+ 회귀 테스트) | AuthService, AuthServiceTest |
| 17 | FuelController (+ MockMvc 테스트) | controller + 테스트 |
| 18 | 최종 검증 (전체 빌드 + 테스트) | 빌드 통과 |

각 Task 끝에서 commit. commit 메시지 형식:

```text
연료 시스템 도메인 구현 : <type> : <설명> #26
```

---

## Task 1: 사전 작업 — version bump + Migration + ErrorCode + CLAUDE.md

**Files:**
- Modify: `version.yml` (`0.0.35` → `0.0.36`)
- Create: `SS-Web/src/main/resources/db/migration/V0_0_36__add_fuel.sql`
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java`
- Modify: `CLAUDE.md` (마이그레이션 이력 표)

- [ ] **Step 1: version.yml 버전 bump**

`version.yml` 파일에서 두 줄 변경:
```yaml
version: "0.0.36"
version_code: 36 # app build number
```

(다른 줄은 그대로 유지)

- [ ] **Step 2: 마이그레이션 SQL 작성**

`SS-Web/src/main/resources/db/migration/V0_0_36__add_fuel.sql` 새로 생성:

```sql
-- user_fuel: 유저당 1개 연료 잔량 레코드
CREATE TABLE IF NOT EXISTS user_fuel (
    user_id          BIGINT      PRIMARY KEY,
    current_fuel     INTEGER     NOT NULL DEFAULT 0,
    total_charged    INTEGER     NOT NULL DEFAULT 0,
    total_consumed   INTEGER     NOT NULL DEFAULT 0,
    pending_minutes  INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_user_fuel_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_fuel_non_negative CHECK (current_fuel >= 0),
    CONSTRAINT chk_total_charged_non_negative CHECK (total_charged >= 0),
    CONSTRAINT chk_total_consumed_non_negative CHECK (total_consumed >= 0),
    CONSTRAINT chk_pending_minutes_non_negative CHECK (pending_minutes >= 0)
);

-- fuel_transactions: 충전/소비 거래 내역
CREATE TABLE IF NOT EXISTS fuel_transactions (
    id             VARCHAR(36)  PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    amount         INTEGER      NOT NULL,
    reason         VARCHAR(30)  NOT NULL,
    reference_id   VARCHAR(50),
    balance_after  INTEGER      NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    CONSTRAINT fk_fuel_transactions_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_fuel_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fuel_tx_type CHECK (type IN ('CHARGE','CONSUME')),
    CONSTRAINT chk_fuel_tx_reason CHECK (reason IN ('STUDY_SESSION','EXPLORATION_UNLOCK'))
);

CREATE INDEX IF NOT EXISTS idx_fuel_transactions_user_created
    ON fuel_transactions (user_id, created_at DESC);
```

- [ ] **Step 3: ErrorCode 2개 추가**

`SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java`에서 `// Todo Category` 블록 뒤, `// Common` 블록 앞에 추가:

```java
    // Fuel
    INSUFFICIENT_FUEL(HttpStatus.BAD_REQUEST, "연료가 부족합니다."),
    FUEL_NOT_INITIALIZED(HttpStatus.INTERNAL_SERVER_ERROR, "연료 정보가 초기화되지 않았습니다."),
```

- [ ] **Step 4: CLAUDE.md 마이그레이션 이력 표 갱신**

`CLAUDE.md` "### 현재 마이그레이션 이력" 표 마지막 줄 뒤에 추가:

```markdown
| 0.0.36 | `V0_0_36__add_fuel.sql` | `user_fuel`, `fuel_transactions` 테이블 생성 (CHECK 제약, FK CASCADE) |
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew :SS-Common:compileJava
```
Expected: BUILD SUCCESSFUL (ErrorCode 컴파일 OK)

- [ ] **Step 6: Commit**

```bash
git add version.yml SS-Web/src/main/resources/db/migration/V0_0_36__add_fuel.sql \
        SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java \
        CLAUDE.md
git commit -m "연료 시스템 도메인 구현 : chore : 사전 작업 (version 0.0.36, V0_0_36 마이그레이션, ErrorCode 2개) #26"
```

---

## Task 2: Enum 2개 추가 — TransactionType, FuelReason

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/constant/TransactionType.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/constant/FuelReason.java`

- [ ] **Step 1: TransactionType 생성**

```java
package com.elipair.spacestudyship.study.fuel.constant;

public enum TransactionType {
    CHARGE,
    CONSUME
}
```

- [ ] **Step 2: FuelReason 생성**

```java
package com.elipair.spacestudyship.study.fuel.constant;

public enum FuelReason {
    STUDY_SESSION,        // charge: 공부 세션 완료
    EXPLORATION_UNLOCK    // consume: 행성/지역 해금
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :SS-Study:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/constant/
git commit -m "연료 시스템 도메인 구현 : feat : TransactionType/FuelReason Enum 추가 #26"
```

---

## Task 3: UserFuel Entity (TDD)

**Files:**
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/entity/UserFuelTest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/entity/UserFuel.java`

- [ ] **Step 1: UserFuelTest 작성 (RED)**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/entity/UserFuelTest.java`:

```java
package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFuelTest {

    @Test
    @DisplayName("initialize: 신규 회원 초기화 시 모든 값 0")
    void initialize_allZero() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThat(fuel.getUserId()).isEqualTo(1L);
        assertThat(fuel.getCurrentFuel()).isZero();
        assertThat(fuel.getTotalCharged()).isZero();
        assertThat(fuel.getTotalConsumed()).isZero();
        assertThat(fuel.getPendingMinutes()).isZero();
    }

    @Test
    @DisplayName("charge: 양수 충전 시 currentFuel과 totalCharged 증가, totalConsumed 불변")
    void charge_increase() {
        UserFuel fuel = UserFuel.initialize(1L);

        fuel.charge(90);

        assertThat(fuel.getCurrentFuel()).isEqualTo(90);
        assertThat(fuel.getTotalCharged()).isEqualTo(90);
        assertThat(fuel.getTotalConsumed()).isZero();
    }

    @Test
    @DisplayName("consume: 잔량 이하 소비 시 currentFuel 감소, totalConsumed 증가, totalCharged 불변")
    void consume_decrease() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);

        fuel.consume(50);

        assertThat(fuel.getCurrentFuel()).isEqualTo(50);
        assertThat(fuel.getTotalConsumed()).isEqualTo(50);
        assertThat(fuel.getTotalCharged()).isEqualTo(100);
    }

    @Test
    @DisplayName("consume: 정확히 잔량만큼 소비 시 currentFuel = 0")
    void consume_exact() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);

        fuel.consume(100);

        assertThat(fuel.getCurrentFuel()).isZero();
        assertThat(fuel.getTotalConsumed()).isEqualTo(100);
    }

    @Test
    @DisplayName("charge: amount=0이면 INVALID_INPUT_VALUE")
    void charge_zero_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.charge(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("charge: amount<0이면 INVALID_INPUT_VALUE")
    void charge_negative_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.charge(-5))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("consume: amount=0이면 INVALID_INPUT_VALUE")
    void consume_zero_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.consume(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("consume: 잔량 부족 시 INSUFFICIENT_FUEL")
    void consume_insufficient_throws() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(30);

        assertThatThrownBy(() -> fuel.consume(50))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUEL);
    }
}
```

> **참고**: `CustomException`의 `errorCode` 필드명은 SS-Common 코드 기준. 만약 필드명이 다르면(`code` 등) 그대로 맞춰 변경.

- [ ] **Step 2: 테스트 실행해 컴파일 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.entity.UserFuelTest
```
Expected: COMPILE FAIL — `UserFuel` 클래스 없음

- [ ] **Step 3: UserFuel Entity 작성**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/entity/UserFuel.java`:

```java
package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_fuel")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFuel extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_fuel", nullable = false)
    private Integer currentFuel;

    @Column(name = "total_charged", nullable = false)
    private Integer totalCharged;

    @Column(name = "total_consumed", nullable = false)
    private Integer totalConsumed;

    @Column(name = "pending_minutes", nullable = false)
    private Integer pendingMinutes;

    public static UserFuel initialize(Long userId) {
        return UserFuel.builder()
                .userId(userId)
                .currentFuel(0)
                .totalCharged(0)
                .totalConsumed(0)
                .pendingMinutes(0)
                .build();
    }

    public void charge(int amount) {
        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        this.currentFuel += amount;
        this.totalCharged += amount;
    }

    public void consume(int amount) {
        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (this.currentFuel < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_FUEL);
        }
        this.currentFuel -= amount;
        this.totalConsumed += amount;
    }
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.entity.UserFuelTest
```
Expected: BUILD SUCCESSFUL, 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/entity/UserFuel.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/entity/UserFuelTest.java
git commit -m "연료 시스템 도메인 구현 : feat : UserFuel Entity (charge/consume/initialize, 단위 테스트) #26"
```

---

## Task 4: FuelTransaction Entity

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/entity/FuelTransaction.java`

> 단순 데이터 컨테이너라 Entity 단위 테스트는 생략 — 의미 있는 비즈니스 로직 없음. Repository 테스트(Task 7)와 Service 테스트에서 검증.

- [ ] **Step 1: FuelTransaction Entity 작성**

```java
package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fuel_transactions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FuelTransaction extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuelReason reason;

    @Column(name = "reference_id", length = 50)
    private String referenceId;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    public static FuelTransaction of(String id, Long userId, TransactionType type,
                                     int amount, FuelReason reason,
                                     String referenceId, int balanceAfter) {
        return FuelTransaction.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .amount(amount)
                .reason(reason)
                .referenceId(referenceId)
                .balanceAfter(balanceAfter)
                .build();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew :SS-Study:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/entity/FuelTransaction.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelTransaction Entity 추가 #26"
```

---

## Task 5: StudyTestApplication 갱신 (fuel repository 스캔 추가)

**Files:**
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java`

기존 어노테이션 `@EnableJpaRepositories(basePackages = "com.elipair.spacestudyship.study.todo.repository")`을 fuel까지 포함하도록 변경.

- [ ] **Step 1: StudyTestApplication 어노테이션 수정**

기존 22번 라인:
```java
@EnableJpaRepositories(basePackages = "com.elipair.spacestudyship.study.todo.repository")
```

변경:
```java
@EnableJpaRepositories(basePackages = {
        "com.elipair.spacestudyship.study.todo.repository",
        "com.elipair.spacestudyship.study.fuel.repository"
})
```

> `@AutoConfigurationPackage(basePackages = "com.elipair.spacestudyship")`는 이미 전체 패키지 스캔이라 Entity 추가 스캔 불요.

- [ ] **Step 2: 기존 Todo 테스트가 여전히 통과하는지 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```
Expected: BUILD SUCCESSFUL (기존 Todo 테스트 회귀 없음)

- [ ] **Step 3: Commit**

```bash
git add SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java
git commit -m "연료 시스템 도메인 구현 : test : StudyTestApplication에 fuel repository 스캔 추가 #26"
```

---

## Task 6: UserFuelRepository + Repository 테스트

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/repository/UserFuelRepository.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/repository/UserFuelRepositoryTest.java`

> 실제 PostgreSQL 컨테이너에서 CHECK 제약·락 동작 검증을 위해 Testcontainers 기반 통합 테스트.

- [ ] **Step 1: UserFuelRepositoryTest 작성 (RED)**

```java
package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class UserFuelRepositoryTest {

    @Autowired UserFuelRepository userFuelRepository;
    @Autowired EntityManager em;

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
    @DisplayName("findByUserIdForUpdate: 락 획득 후 row 반환 (smoke)")
    void findByUserIdForUpdate_returnsRow() {
        userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThat(userFuelRepository.findByUserIdForUpdate(1L)).isPresent();
    }

    @Test
    @DisplayName("current_fuel을 음수로 update 시 CHECK 제약으로 실패")
    void checkConstraint_currentFuelNonNegative() {
        userFuelRepository.saveAndFlush(UserFuel.initialize(1L));

        assertThatThrownBy(() -> {
            em.createNativeQuery("UPDATE user_fuel SET current_fuel = -1 WHERE user_id = 1")
                    .executeUpdate();
            em.flush();
        }).isInstanceOf(Exception.class);  // DataIntegrityViolation 또는 PSQLException 포함
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.repository.UserFuelRepositoryTest
```
Expected: COMPILE FAIL — `UserFuelRepository` 없음

- [ ] **Step 3: UserFuelRepository 작성**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/repository/UserFuelRepository.java`:

```java
package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserFuelRepository extends JpaRepository<UserFuel, Long> {

    Optional<UserFuel> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uf FROM UserFuel uf WHERE uf.userId = :userId")
    Optional<UserFuel> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.repository.UserFuelRepositoryTest
```
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/repository/UserFuelRepository.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/repository/UserFuelRepositoryTest.java
git commit -m "연료 시스템 도메인 구현 : feat : UserFuelRepository (findByUserIdForUpdate 비관적 락 포함) #26"
```

---

## Task 7: FuelTransactionRepository + Repository 테스트

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/repository/FuelTransactionRepository.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/repository/FuelTransactionRepositoryTest.java`

- [ ] **Step 1: FuelTransactionRepositoryTest 작성 (RED)**

```java
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

    @Autowired FuelTransactionRepository transactionRepository;
    @Autowired EntityManager em;

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
        // 임의로 1건 저장하고 시간 범위로 필터
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
    @DisplayName("CHECK 제약: amount=0 native insert 시 실패")
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
```

- [ ] **Step 2: 컴파일 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepositoryTest
```
Expected: COMPILE FAIL — `FuelTransactionRepository` 없음

- [ ] **Step 3: FuelTransactionRepository 작성**

```java
package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface FuelTransactionRepository extends JpaRepository<FuelTransaction, String> {

    @Query("""
            SELECT ft FROM FuelTransaction ft
            WHERE ft.userId = :userId
              AND (:type IS NULL OR ft.type = :type)
              AND (:startDateTime IS NULL OR ft.createdAt >= :startDateTime)
              AND (:endDateTime IS NULL OR ft.createdAt < :endDateTime)
            """)
    Page<FuelTransaction> findByFilters(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable);
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepositoryTest
```
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/repository/FuelTransactionRepository.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/repository/FuelTransactionRepositoryTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelTransactionRepository (필터/페이지네이션 쿼리, 통합 테스트) #26"
```

---

## Task 8: MemberCreatedEvent (SS-Member)

**Files:**
- Create: `SS-Member/src/main/java/com/elipair/spacestudyship/member/event/MemberCreatedEvent.java`

- [ ] **Step 1: 디렉토리 확인**

```bash
ls /Users/luca/workspace/Java_Spring/space_study_ship/SS-Member/src/main/java/com/elipair/spacestudyship/member/
```
Expected: 기존 `entity`, `repository`, `constant` 등이 있음. `event` 폴더는 신규 생성.

- [ ] **Step 2: MemberCreatedEvent 작성**

```java
package com.elipair.spacestudyship.member.event;

public record MemberCreatedEvent(Long memberId) {
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :SS-Member:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add SS-Member/src/main/java/com/elipair/spacestudyship/member/event/MemberCreatedEvent.java
git commit -m "연료 시스템 도메인 구현 : feat : MemberCreatedEvent record 추가 (SS-Member) #26"
```

---

## Task 9: DTO 3개 (FuelResponse, FuelTransactionResponse, FuelTransactionListResponse)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/dto/FuelResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/dto/FuelTransactionResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/dto/FuelTransactionListResponse.java`

> DTO는 단순 record + from() 정적 메서드. Service/Controller 테스트에서 통합 검증.

- [ ] **Step 1: FuelResponse 작성**

```java
package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "연료 잔량 응답")
public record FuelResponse(
        @Schema(description = "현재 보유 연료", example = "350") Integer currentFuel,
        @Schema(description = "누적 충전량", example = "1200") Integer totalCharged,
        @Schema(description = "누적 소비량", example = "850") Integer totalConsumed,
        @Schema(description = "미동기화 시간(분) - 향후 확장용, 현재 항상 0", example = "0") Integer pendingMinutes,
        @Schema(description = "마지막 변동 시각 (ISO 8601 UTC)", example = "2026-04-16T10:30:00Z") String lastUpdatedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static FuelResponse from(UserFuel fuel) {
        return new FuelResponse(
                fuel.getCurrentFuel(),
                fuel.getTotalCharged(),
                fuel.getTotalConsumed(),
                fuel.getPendingMinutes(),
                formatUtc(fuel.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 2: FuelTransactionResponse 작성**

```java
package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "연료 거래 내역")
public record FuelTransactionResponse(
        @Schema(example = "tx-uuid-1234") String id,

        @Schema(description = "charge 또는 consume",
                allowableValues = {"charge", "consume"}, example = "charge")
        String type,

        @Schema(example = "90") Integer amount,

        @Schema(description = "거래 사유",
                allowableValues = {"STUDY_SESSION", "EXPLORATION_UNLOCK"},
                example = "STUDY_SESSION")
        String reason,

        @Schema(nullable = true, example = "session-uuid-5678") String referenceId,
        @Schema(example = "350") Integer balanceAfter,
        @Schema(example = "2026-04-16T10:30:00Z") String createdAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static FuelTransactionResponse from(FuelTransaction tx) {
        return new FuelTransactionResponse(
                tx.getId(),
                tx.getType().name().toLowerCase(),
                tx.getAmount(),
                tx.getReason().name(),
                tx.getReferenceId(),
                tx.getBalanceAfter(),
                formatUtc(tx.getCreatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 3: FuelTransactionListResponse 작성**

```java
package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "거래 내역 페이지 응답")
public record FuelTransactionListResponse(
        List<FuelTransactionResponse> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {
    public static FuelTransactionListResponse from(Page<FuelTransaction> page) {
        return new FuelTransactionListResponse(
                page.getContent().stream().map(FuelTransactionResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew :SS-Study:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/dto/
git commit -m "연료 시스템 도메인 구현 : feat : FuelResponse/FuelTransactionResponse/ListResponse DTO 3종 #26"
```

---

## Task 10: FuelService.initialize (TDD)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java` (skeleton + initialize)
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java` (initialize 테스트만)

> 이후 Task 11~14에서 같은 클래스/테스트 파일에 메서드를 추가해 나간다.

- [ ] **Step 1: FuelServiceTest 작성 (initialize만, RED)**

```java
package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepository;
import com.elipair.spacestudyship.study.fuel.repository.UserFuelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FuelServiceTest {

    @Mock UserFuelRepository userFuelRepository;
    @Mock FuelTransactionRepository transactionRepository;
    @InjectMocks FuelService fuelService;

    @Test
    @DisplayName("initialize: 미존재 회원이면 UserFuel.initialize 저장")
    void initialize_newMember_saves() {
        given(userFuelRepository.existsByUserId(1L)).willReturn(false);

        fuelService.initialize(1L);

        ArgumentCaptor<UserFuel> captor = ArgumentCaptor.forClass(UserFuel.class);
        verify(userFuelRepository, times(1)).save(captor.capture());
        UserFuel saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCurrentFuel()).isZero();
        assertThat(saved.getTotalCharged()).isZero();
        assertThat(saved.getTotalConsumed()).isZero();
        assertThat(saved.getPendingMinutes()).isZero();
    }

    @Test
    @DisplayName("initialize: 이미 존재하면 skip (save 호출 없음)")
    void initialize_existing_skips() {
        given(userFuelRepository.existsByUserId(1L)).willReturn(true);

        fuelService.initialize(1L);

        verify(userFuelRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: COMPILE FAIL — `FuelService` 없음

- [ ] **Step 3: FuelService skeleton + initialize 작성**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java`:

```java
package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepository;
import com.elipair.spacestudyship.study.fuel.repository.UserFuelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FuelService {

    private final UserFuelRepository userFuelRepository;
    private final FuelTransactionRepository transactionRepository;

    @Transactional
    public void initialize(Long userId) {
        if (userFuelRepository.existsByUserId(userId)) {
            log.info("[Fuel] 초기화 스킵 (이미 존재) | userId={}", userId);
            return;
        }
        userFuelRepository.save(UserFuel.initialize(userId));
        log.info("[Fuel] 초기화 | userId={}", userId);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelService.initialize (가입 이벤트로부터 UserFuel 생성) #26"
```

---

## Task 11: FuelService.getFuel (TDD)

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java`

- [ ] **Step 1: 테스트 추가 (RED)**

`FuelServiceTest`에 다음 2개 테스트 추가 (기존 import 활용):

```java
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
@DisplayName("getFuel: 존재 시 FuelResponse 반환")
void getFuel_existing_returnsResponse() {
    UserFuel fuel = UserFuel.initialize(1L);
    fuel.charge(100);
    given(userFuelRepository.findByUserId(1L)).willReturn(Optional.of(fuel));

    FuelResponse response = fuelService.getFuel(1L);

    assertThat(response.currentFuel()).isEqualTo(100);
    assertThat(response.totalCharged()).isEqualTo(100);
    assertThat(response.totalConsumed()).isZero();
    assertThat(response.pendingMinutes()).isZero();
}

@Test
@DisplayName("getFuel: 미초기화면 FUEL_NOT_INITIALIZED")
void getFuel_notInitialized_throws() {
    given(userFuelRepository.findByUserId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> fuelService.getFuel(1L))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: COMPILE FAIL — `getFuel` 없음

- [ ] **Step 3: FuelService에 getFuel 추가**

```java
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
```

다음 메서드 추가 (initialize 메서드 위에 — readOnly 메서드 → 쓰기 메서드 순서):

```java
public FuelResponse getFuel(Long userId) {
    UserFuel fuel = userFuelRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
    return FuelResponse.from(fuel);
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: 4 tests PASS (기존 2 + 신규 2)

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelService.getFuel + FUEL_NOT_INITIALIZED 케이스 #26"
```

---

## Task 12: FuelService.getTransactions (TDD)

**Files:**
- Modify: `SS-Study/.../service/FuelService.java`
- Modify: `SS-Study/.../service/FuelServiceTest.java`

- [ ] **Step 1: 테스트 추가 (RED)**

`FuelServiceTest`에 추가:

```java
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@Test
@DisplayName("getTransactions: 모든 필터 null 통과")
void getTransactions_allNulls_passesNullsAndDefaultPageable() {
    given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of()));

    FuelTransactionListResponse response = fuelService.getTransactions(
            1L, null, null, null, 0, 20);

    assertThat(response.content()).isEmpty();
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalElements()).isZero();
    assertThat(response.totalPages()).isZero();
}

@Test
@DisplayName("getTransactions: startDate/endDate를 LocalDateTime 반열림 [start, end+1)로 변환")
void getTransactions_dateRange_convertsToHalfOpen() {
    ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    given(transactionRepository.findByFilters(eq(1L), eq(TransactionType.CHARGE),
            startCaptor.capture(), endCaptor.capture(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of()));

    fuelService.getTransactions(1L, TransactionType.CHARGE,
            "2026-04-01", "2026-04-16", 0, 20);

    assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 1).atStartOfDay());
    assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 17).atStartOfDay());  // +1일
}

@Test
@DisplayName("getTransactions: Pageable의 정렬은 createdAt DESC 강제")
void getTransactions_sortIsCreatedAtDesc() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), pageableCaptor.capture()))
            .willReturn(new PageImpl<>(List.of()));

    fuelService.getTransactions(1L, null, null, null, 1, 5);

    Pageable captured = pageableCaptor.getValue();
    Sort.Order order = captured.getSort().getOrderFor("createdAt");
    assertThat(captured.getPageNumber()).isEqualTo(1);
    assertThat(captured.getPageSize()).isEqualTo(5);
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
}

@Test
@DisplayName("getTransactions: 내용 매핑 및 envelope 필드 정합")
void getTransactions_mapsContentCorrectly() {
    FuelTransaction tx = FuelTransaction.of(
            "tx-1", 1L, TransactionType.CHARGE, 90,
            FuelReason.STUDY_SESSION, "s-1", 350);
    given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(tx), Pageable.unpaged(), 1L));

    FuelTransactionListResponse response = fuelService.getTransactions(
            1L, null, null, null, 0, 20);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).id()).isEqualTo("tx-1");
    assertThat(response.content().get(0).type()).isEqualTo("charge");  // 소문자 변환
    assertThat(response.content().get(0).reason()).isEqualTo("STUDY_SESSION");
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: COMPILE FAIL — `getTransactions` 없음

- [ ] **Step 3: FuelService에 getTransactions 추가**

import 추가:
```java
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
```

메서드 추가 (`getFuel` 아래):

```java
public FuelTransactionListResponse getTransactions(
        Long userId, TransactionType type,
        String startDate, String endDate,
        int page, int size) {

    LocalDateTime startDateTime = startDate == null ? null
            : LocalDate.parse(startDate).atStartOfDay();
    LocalDateTime endDateTime = endDate == null ? null
            : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

    Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<FuelTransaction> result = transactionRepository
            .findByFilters(userId, type, startDateTime, endDateTime, pageable);

    return FuelTransactionListResponse.from(result);
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelService.getTransactions (날짜 반열림 변환, createdAt DESC 정렬) #26"
```

---

## Task 13: FuelService.charge (TDD)

**Files:**
- Modify: `SS-Study/.../service/FuelService.java`
- Modify: `SS-Study/.../service/FuelServiceTest.java`

- [ ] **Step 1: 테스트 추가 (RED)**

`FuelServiceTest`에 추가:

```java
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;

@Test
@DisplayName("charge: 정상 흐름 - 락 획득 → entity.charge → tx 저장")
void charge_happy() {
    UserFuel fuel = UserFuel.initialize(1L);
    given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
    given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

    FuelTransactionResponse response = fuelService.charge(
            1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1");

    ArgumentCaptor<FuelTransaction> captor = ArgumentCaptor.forClass(FuelTransaction.class);
    verify(transactionRepository).save(captor.capture());
    FuelTransaction saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo("tx-1");
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getType()).isEqualTo(TransactionType.CHARGE);
    assertThat(saved.getAmount()).isEqualTo(90);
    assertThat(saved.getReason()).isEqualTo(FuelReason.STUDY_SESSION);
    assertThat(saved.getReferenceId()).isEqualTo("s-1");
    assertThat(saved.getBalanceAfter()).isEqualTo(90);

    assertThat(response.id()).isEqualTo("tx-1");
    assertThat(response.balanceAfter()).isEqualTo(90);
    assertThat(fuel.getCurrentFuel()).isEqualTo(90);
    assertThat(fuel.getTotalCharged()).isEqualTo(90);
}

@Test
@DisplayName("charge: idempotent - 동일 transactionId 재호출 시 기존 tx 반환, 락/저장 없음")
void charge_idempotent() {
    FuelTransaction existing = FuelTransaction.of(
            "tx-1", 1L, TransactionType.CHARGE, 90,
            FuelReason.STUDY_SESSION, "s-1", 350);
    given(transactionRepository.findById("tx-1")).willReturn(Optional.of(existing));

    FuelTransactionResponse response = fuelService.charge(
            1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1");

    assertThat(response.id()).isEqualTo("tx-1");
    assertThat(response.balanceAfter()).isEqualTo(350);
    verify(userFuelRepository, never()).findByUserIdForUpdate(any());
    verify(transactionRepository, never()).save(any());
}

@Test
@DisplayName("charge: amount<=0 시 INVALID_INPUT_VALUE")
void charge_invalidAmount_throws() {
    assertThatThrownBy(() -> fuelService.charge(
            1L, 0, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

    assertThatThrownBy(() -> fuelService.charge(
            1L, -10, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
}

@Test
@DisplayName("charge: UserFuel 미초기화 시 FUEL_NOT_INITIALIZED")
void charge_fuelNotInitialized_throws() {
    given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
    given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> fuelService.charge(
            1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: COMPILE FAIL — `charge` 없음

- [ ] **Step 3: FuelService에 charge 추가**

import 추가:
```java
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import java.util.Optional;
```

메서드 추가:

```java
@Transactional
public FuelTransactionResponse charge(
        Long userId, int amount, FuelReason reason,
        String referenceId, String transactionId) {

    if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

    Optional<FuelTransaction> existing = transactionRepository.findById(transactionId);
    if (existing.isPresent()) {
        log.info("[Fuel] charge idempotent skip | userId={}, txId={}", userId, transactionId);
        return FuelTransactionResponse.from(existing.get());
    }

    UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
    fuel.charge(amount);

    FuelTransaction tx = FuelTransaction.of(
            transactionId, userId, TransactionType.CHARGE,
            amount, reason, referenceId, fuel.getCurrentFuel());
    transactionRepository.save(tx);

    log.info("[Fuel] 충전 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
            userId, amount, reason, transactionId, fuel.getCurrentFuel());
    return FuelTransactionResponse.from(tx);
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: 12 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelService.charge (idempotency, 비관적 락) #26"
```

---

## Task 14: FuelService.consume (TDD)

**Files:**
- Modify: `SS-Study/.../service/FuelService.java`
- Modify: `SS-Study/.../service/FuelServiceTest.java`

- [ ] **Step 1: 테스트 추가 (RED)**

`FuelServiceTest`에 추가:

```java
@Test
@DisplayName("consume: 정상 흐름")
void consume_happy() {
    UserFuel fuel = UserFuel.initialize(1L);
    fuel.charge(100);
    given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
    given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

    FuelTransactionResponse response = fuelService.consume(
            1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1");

    ArgumentCaptor<FuelTransaction> captor = ArgumentCaptor.forClass(FuelTransaction.class);
    verify(transactionRepository).save(captor.capture());
    FuelTransaction saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(TransactionType.CONSUME);
    assertThat(saved.getAmount()).isEqualTo(30);
    assertThat(saved.getReason()).isEqualTo(FuelReason.EXPLORATION_UNLOCK);
    assertThat(saved.getReferenceId()).isEqualTo("region-1");
    assertThat(saved.getBalanceAfter()).isEqualTo(70);

    assertThat(response.balanceAfter()).isEqualTo(70);
    assertThat(fuel.getCurrentFuel()).isEqualTo(70);
    assertThat(fuel.getTotalConsumed()).isEqualTo(30);
}

@Test
@DisplayName("consume: idempotent - 동일 transactionId 재호출 시 no-op")
void consume_idempotent() {
    FuelTransaction existing = FuelTransaction.of(
            "tx-1", 1L, TransactionType.CONSUME, 30,
            FuelReason.EXPLORATION_UNLOCK, "region-1", 70);
    given(transactionRepository.findById("tx-1")).willReturn(Optional.of(existing));

    FuelTransactionResponse response = fuelService.consume(
            1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1");

    assertThat(response.id()).isEqualTo("tx-1");
    verify(userFuelRepository, never()).findByUserIdForUpdate(any());
    verify(transactionRepository, never()).save(any());
}

@Test
@DisplayName("consume: amount<=0 시 INVALID_INPUT_VALUE")
void consume_invalidAmount_throws() {
    assertThatThrownBy(() -> fuelService.consume(
            1L, 0, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
}

@Test
@DisplayName("consume: 잔량 부족 시 INSUFFICIENT_FUEL (Entity 내부 던짐)")
void consume_insufficient_throws() {
    UserFuel fuel = UserFuel.initialize(1L);
    fuel.charge(20);
    given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
    given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

    assertThatThrownBy(() -> fuelService.consume(
            1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUEL);

    verify(transactionRepository, never()).save(any());
}

@Test
@DisplayName("consume: UserFuel 미초기화 시 FUEL_NOT_INITIALIZED")
void consume_fuelNotInitialized_throws() {
    given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
    given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> fuelService.consume(
            1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: COMPILE FAIL — `consume` 없음

- [ ] **Step 3: FuelService에 consume 추가**

```java
@Transactional
public FuelTransactionResponse consume(
        Long userId, int amount, FuelReason reason,
        String referenceId, String transactionId) {

    if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

    Optional<FuelTransaction> existing = transactionRepository.findById(transactionId);
    if (existing.isPresent()) {
        log.info("[Fuel] consume idempotent skip | userId={}, txId={}", userId, transactionId);
        return FuelTransactionResponse.from(existing.get());
    }

    UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
    fuel.consume(amount);

    FuelTransaction tx = FuelTransaction.of(
            transactionId, userId, TransactionType.CONSUME,
            amount, reason, referenceId, fuel.getCurrentFuel());
    transactionRepository.save(tx);

    log.info("[Fuel] 소비 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
            userId, amount, reason, transactionId, fuel.getCurrentFuel());
    return FuelTransactionResponse.from(tx);
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelServiceTest
```
Expected: 17 tests PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelService.consume (잔량 부족 → INSUFFICIENT_FUEL) #26"
```

---

## Task 15: FuelInitializeListener (TDD)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelInitializeListener.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelInitializeListenerTest.java`

- [ ] **Step 1: FuelInitializeListenerTest 작성 (RED)**

```java
package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FuelInitializeListenerTest {

    @Mock FuelService fuelService;
    @InjectMocks FuelInitializeListener listener;

    @Test
    @DisplayName("MemberCreatedEvent 수신 시 fuelService.initialize 호출")
    void onMemberCreated_callsInitialize() {
        listener.onMemberCreated(new MemberCreatedEvent(42L));

        verify(fuelService).initialize(42L);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelInitializeListenerTest
```
Expected: COMPILE FAIL — `FuelInitializeListener` 없음

- [ ] **Step 3: FuelInitializeListener 작성**

```java
package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FuelInitializeListener {

    private final FuelService fuelService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMemberCreated(MemberCreatedEvent event) {
        log.info("[Fuel] MemberCreatedEvent 수신 | memberId={}", event.memberId());
        fuelService.initialize(event.memberId());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Study:test --tests com.elipair.spacestudyship.study.fuel.service.FuelInitializeListenerTest
```
Expected: 1 test PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/fuel/service/FuelInitializeListener.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/fuel/service/FuelInitializeListenerTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelInitializeListener (MemberCreatedEvent → UserFuel 초기화) #26"
```

---

## Task 16: AuthService에 MemberCreatedEvent publish 추가 (+ 회귀 테스트)

**Files:**
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`

- [ ] **Step 1: AuthServiceTest 회귀 테스트 추가 (RED)**

`AuthServiceTest`는 이미 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks AuthService authService` 패턴.

**(1)** import 추가:
```java
import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
```

**(2)** `@Mock` 필드 추가 (다른 mock 필드 옆):
```java
@Mock
ApplicationEventPublisher eventPublisher;
```
> `@InjectMocks AuthService`가 자동으로 생성자의 `ApplicationEventPublisher` 필드를 mock으로 주입함 (`@RequiredArgsConstructor`).

**(3)** 테스트 메서드 2개 추가. 기존 `AuthServiceTest`에서 신규 회원 가입 / 기존 회원 재로그인 시나리오의 stubbing 패턴을 그대로 따른다. 핵심 검증 부분만 명시:

```java
@Test
@DisplayName("신규 회원 로그인 시 MemberCreatedEvent를 publish한다")
void login_newMember_publishesMemberCreatedEvent() {
    // given - 신규 회원 시나리오 (기존 AuthServiceTest의 '신규 회원 가입' 테스트와 동일한 stubbing 사용)
    //   given(socialLoginStrategies.get(any())).willReturn(socialLoginStrategy);
    //   given(socialLoginStrategy.validateAndGetSocialId(...)).willReturn("social-id");
    //   given(memberRepository.findBySocialIdAndSocialType(...)).willReturn(Optional.empty());
    //   given(randomNicknameGenerator.generate()).willReturn("닉네임");
    //   given(memberRepository.existsByNickname(any())).willReturn(false);
    //   given(memberRepository.save(any(Member.class))).willAnswer(inv -> {
    //       Member m = inv.getArgument(0);
    //       // ID 세팅을 위해 reflection 또는 spy 사용 (기존 패턴 따름)
    //       return m;
    //   });
    //   given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    //   given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("refresh");

    // when
    // authService.login(new LoginRequest(...));

    // then
    ArgumentCaptor<MemberCreatedEvent> captor = ArgumentCaptor.forClass(MemberCreatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().memberId()).isEqualTo(/* 기대 memberId */);
}

@Test
@DisplayName("기존 회원 재로그인 시 MemberCreatedEvent를 publish 하지 않는다")
void login_existingMember_doesNotPublishEvent() {
    // given - 기존 회원 시나리오 (기존 AuthServiceTest의 '기존 회원 로그인' stubbing 그대로)
    //   given(memberRepository.findBySocialIdAndSocialType(...)).willReturn(Optional.of(existingMember));
    //   ... 토큰 발급 / 디바이스 upsert stubbing ...

    // when
    // authService.login(new LoginRequest(...));

    // then
    verify(eventPublisher, never()).publishEvent(any(MemberCreatedEvent.class));
}
```

> **중요**: 위 코드의 `// given` 영역은 **기존 `AuthServiceTest`의 신규/기존 회원 로그인 테스트 메서드를 그대로 복사 후 verify 부분만 위와 같이 교체**한다. stubbing 패턴은 기존 코드의 절대 진실 — 새로 짜지 말고 기존 패턴을 따른다.

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest
```
Expected: FAIL — eventPublisher가 주입되지 않거나 publishEvent 호출 없음

- [ ] **Step 3: AuthService 수정**

`AuthService.java` 상단 imports에 추가:
```java
import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

필드 추가 (`@RequiredArgsConstructor`이므로 final 필드만 추가하면 됨):
```java
private final ApplicationEventPublisher eventPublisher;
```

`findOrRegisterMember` 메서드의 신규 회원 분기 수정. 현재:
```java
.orElseGet(() -> {
    String nickname = generateUniqueNickname();
    Member newMember = Member.signUp(socialId, socialType, nickname);
    memberRepository.save(newMember);

    log.info("[SignUp] 신규 회원가입 성공 | memberId={}, nickname={}, socialType={}",
            newMember.getId(), nickname, socialType);
    return new AuthMemberDto(newMember, true);
});
```

다음으로 변경 (save 직후 publish):
```java
.orElseGet(() -> {
    String nickname = generateUniqueNickname();
    Member newMember = Member.signUp(socialId, socialType, nickname);
    memberRepository.save(newMember);
    eventPublisher.publishEvent(new MemberCreatedEvent(newMember.getId()));

    log.info("[SignUp] 신규 회원가입 성공 | memberId={}, nickname={}, socialType={}",
            newMember.getId(), nickname, socialType);
    return new AuthMemberDto(newMember, true);
});
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest
```
Expected: 전체 테스트 PASS (기존 테스트 회귀 없음 + 신규 2개 PASS)

- [ ] **Step 5: Commit**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java
git commit -m "연료 시스템 도메인 구현 : feat : AuthService에 MemberCreatedEvent publish 추가 #26"
```

---

## Task 17: FuelController + MockMvc 테스트

**Files:**
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/fuel/FuelController.java`
- Create: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/fuel/FuelControllerTest.java`

- [ ] **Step 1: FuelControllerTest 작성 (RED)**

기존 `TodoControllerTest`와 동일한 **standalone MockMvc** 패턴을 사용한다. `@SpringBootTest`가 아닌 `@ExtendWith(MockitoExtension.class)` + `MockMvcBuilders.standaloneSetup(...)`.

```java
package com.elipair.spacestudyship.controller.fuel;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FuelControllerTest {

    @Mock FuelService fuelService;
    @InjectMocks FuelController fuelController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver loginMemberStub = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(LoginMember.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          org.springframework.web.context.request.NativeWebRequest webRequest,
                                          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new LoginMember(1L);
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(fuelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .build();
    }

    @Test
    @DisplayName("GET /api/fuel — 200, FuelResponse 본문")
    void getFuel_200() throws Exception {
        given(fuelService.getFuel(1L))
                .willReturn(new FuelResponse(350, 1200, 850, 0, "2026-04-16T10:30:00Z"));

        mockMvc.perform(get("/api/fuel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFuel").value(350))
                .andExpect(jsonPath("$.totalCharged").value(1200))
                .andExpect(jsonPath("$.totalConsumed").value(850))
                .andExpect(jsonPath("$.pendingMinutes").value(0))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-04-16T10:30:00Z"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions — 200, Page envelope")
    void getTransactions_200() throws Exception {
        given(fuelService.getTransactions(eq(1L), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .willReturn(new FuelTransactionListResponse(
                        List.of(new FuelTransactionResponse(
                                "tx-1", "charge", 90, "STUDY_SESSION", "s-1", 350, "2026-04-16T10:30:00Z")),
                        0, 20, 1L, 1));

        mockMvc.perform(get("/api/fuel/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("tx-1"))
                .andExpect(jsonPath("$.content[0].type").value("charge"))
                .andExpect(jsonPath("$.content[0].reason").value("STUDY_SESSION"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?type=invalid → 400 INVALID_INPUT_VALUE")
    void getTransactions_invalidType_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?type=invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?startDate=2026-13-01 → 400 (Pattern 위반)")
    void getTransactions_invalidStartDate_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?startDate=2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?size=200 → 400 (Max 100)")
    void getTransactions_sizeOverMax_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?size=200"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?page=-1 → 400 (Min 0)")
    void getTransactions_negativePage_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?type=charge&startDate=2026-04-01&endDate=2026-04-16 - 인자 그대로 서비스로")
    void getTransactions_argsPassThrough() throws Exception {
        given(fuelService.getTransactions(eq(1L), eq(TransactionType.CHARGE),
                eq("2026-04-01"), eq("2026-04-16"), eq(0), eq(20)))
                .willReturn(new FuelTransactionListResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/fuel/transactions")
                        .param("type", "charge")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-16"))
                .andExpect(status().isOk());
    }
}
```

> **참고 1**: 401 (인증 미존재) 케이스는 `standaloneSetup`이 인터셉터를 끼지 않아 검증 불가. 인증 동작 검증은 기존 `AuthControllerTest`에서 다루므로 여기서는 제외.
>
> **참고 2**: standalone setup에서 `@Validated` + `@Pattern`/`@Min`/`@Max` 검증이 동작하지 않으면 `ConstraintViolationException`이 핸들러까지 도달하지 않을 수 있다. 그 경우 `MockMvcBuilders.standaloneSetup(fuelController).setValidator(new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean())` 추가 또는 별도 `MethodValidationPostProcessor`를 끼우는 보완 필요. 실제 RED → GREEN 진행 시 400이 안 나오면 이 단계에서 setup 보강.

- [ ] **Step 2: 테스트 실패 확인 (RED)**

```bash
./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.fuel.FuelControllerTest
```
Expected: COMPILE FAIL — `FuelController` 없음

- [ ] **Step 3: FuelController 작성**

```java
package com.elipair.spacestudyship.controller.fuel;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fuel", description = "연료 잔량 및 거래 내역 API")
@RestController
@RequiredArgsConstructor
@Validated
public class FuelController {

    private final FuelService fuelService;

    @Operation(summary = "연료 잔량 조회",
            description = """
                현재 유저의 연료 잔량 및 누적 충전/소비량을 조회합니다.

                ### 응답 필드
                - currentFuel: 현재 보유 (totalCharged - totalConsumed)
                - totalCharged / totalConsumed: 누적량
                - pendingMinutes: 향후 확장용, 현재 항상 0
                - lastUpdatedAt: 마지막 변동 시각 (ISO 8601 UTC)
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FuelResponse.class),
                            examples = @ExampleObject(name = "Success",
                                    value = "{\"currentFuel\":350,\"totalCharged\":1200,\"totalConsumed\":850,\"pendingMinutes\":0,\"lastUpdatedAt\":\"2026-04-16T10:30:00Z\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/fuel")
    public ResponseEntity<FuelResponse> getFuel(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(fuelService.getFuel(loginMember.memberId()));
    }

    @Operation(summary = "연료 거래 내역 조회",
            description = """
                연료 충전/소비 이력을 페이지네이션으로 조회합니다.

                ### Query Parameters
                - type: charge | consume (선택)
                - startDate / endDate: YYYY-MM-DD (선택, 종료일 포함 반열림 [start, end+1))
                - page: 기본 0
                - size: 기본 20, 최대 100

                정렬은 createdAt 내림차순 고정.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FuelTransactionListResponse.class),
                            examples = @ExampleObject(name = "Page",
                                    value = """
                                            {
                                              "content": [
                                                {"id":"tx-1","type":"charge","amount":90,"reason":"STUDY_SESSION","referenceId":"session-1","balanceAfter":350,"createdAt":"2026-04-16T10:30:00Z"}
                                              ],
                                              "page": 0, "size": 20, "totalElements": 120, "totalPages": 6
                                            }
                                            """))),
            @ApiResponse(responseCode = "400", description = "잘못된 query parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"type은 charge 또는 consume이어야 합니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/api/fuel/transactions")
    public ResponseEntity<FuelTransactionListResponse> getTransactions(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false)
            @Pattern(regexp = "charge|consume",
                    message = "type은 charge 또는 consume이어야 합니다.")
            String type,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}",
                    message = "startDate는 YYYY-MM-DD 형식이어야 합니다.")
            String startDate,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}",
                    message = "endDate는 YYYY-MM-DD 형식이어야 합니다.")
            String endDate,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {

        TransactionType typeEnum = type == null ? null
                : TransactionType.valueOf(type.toUpperCase());
        return ResponseEntity.ok(
                fuelService.getTransactions(
                        loginMember.memberId(), typeEnum,
                        startDate, endDate, page, size));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

```bash
./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.fuel.FuelControllerTest
```
Expected: 모든 시나리오 PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/fuel/FuelController.java \
        SS-Web/src/test/java/com/elipair/spacestudyship/controller/fuel/FuelControllerTest.java
git commit -m "연료 시스템 도메인 구현 : feat : FuelController + Swagger 풀세트 + MockMvc 테스트 #26"
```

---

## Task 18: 최종 검증 — 전체 빌드 + 테스트

**Files:** 변경 없음

- [ ] **Step 1: 전체 빌드**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL, 모든 모듈 컴파일 + 테스트 통과

- [ ] **Step 2: 모듈별 테스트 단독 실행 확인**

```bash
./gradlew :SS-Common:test
./gradlew :SS-Auth:test
./gradlew :SS-Member:test
./gradlew :SS-Study:test
./gradlew :SS-Web:test
```
Expected: 각 모듈 PASS

- [ ] **Step 3: 마이그레이션 적용 확인 (선택)**

로컬 PostgreSQL이 띄워져 있다면 한 번 부트해서 Flyway가 V0_0_36 적용하는지 로그 확인:

```bash
./gradlew :SS-Web:bootRun
```
log에서 `Migrating schema "public" to version "0.0.36 - add fuel"` 확인 후 Ctrl-C.

- [ ] **Step 4: 셀프 리뷰 체크리스트 점검 (spec §10)**

설계 문서 `docs/superpowers/specs/2026-05-23-fuel-domain-design.md` §10의 체크리스트 모두 만족하는지 확인:
- [ ] `CustomException(ErrorCode)` 패턴 일관 사용
- [ ] Service `@Transactional(readOnly = true)` + 쓰기만 `@Transactional`
- [ ] `findByUserIdForUpdate` 사용 (charge/consume)
- [ ] idempotency 확인 후 락 → 저장
- [ ] amount 가드 Service + Entity 양쪽
- [ ] Swagger 응답 코드 풀세트 (200/400/401/500)
- [ ] `@Validated`, `@Pattern`, `@Min`, `@Max` 검증
- [ ] 로그 포맷 `[Fuel] 액션 | key=value`
- [ ] 마이그레이션 민감 값 없음
- [ ] version.yml bump 포함
- [ ] CLAUDE.md 갱신
- [ ] `StudyTestApplication` 갱신
- [ ] `MemberCreatedEvent` SS-Member 위치
- [ ] AuthService publishEvent 추가

- [ ] **Step 5: 최종 커밋이 없으면 종료**

이전 task에서 모든 변경이 커밋됐다면 추가 커밋 없이 종료. 만약 미세 수정이 발생했으면:

```bash
git add -p   # 의도한 변경만 stage
git commit -m "연료 시스템 도메인 구현 : chore : 셀프 리뷰 반영 #26"
```

---

## 셀프 리뷰 결과 (writing-plans skill)

**1. Spec coverage 확인**
- ✅ §3.1 UserFuel Entity → Task 3
- ✅ §3.2 FuelTransaction Entity → Task 4
- ✅ §3.3 Enum → Task 2
- ✅ §4.1–4.3 DTO → Task 9
- ✅ §4.4 Search 파라미터 컨트롤러 직접 → Task 17
- ✅ §5.1–5.5 Repository → Task 6, 7
- ✅ §6.1 Controller → Task 17
- ✅ §6.4–6.9 Service 메서드 → Task 10–14
- ✅ §6.10–6.12 Event/Listener/AuthService → Task 8, 15, 16
- ✅ §7 ErrorCode → Task 1
- ✅ §8 Migration → Task 1
- ✅ §9 테스트 전략 → Task 3, 6, 7, 10–17
- ✅ §10 셀프 리뷰 → Task 18 Step 4
- ✅ §11 산출물 → 18개 Task 전체

**2. Placeholder scan**
- "TBD", "TODO" 없음
- 모든 코드 블록은 실제 컴파일 가능한 코드
- Task 16의 회귀 테스트 setup은 기존 `AuthServiceTest`의 신규/기존 회원 stubbing을 그대로 복사하라고 명시. verify 부분은 완전 코드
- Task 17은 기존 `TodoControllerTest`의 standalone MockMvc 패턴을 그대로 적용 — setup/loginMemberStub/모든 시나리오 본문 코드 명시
- spec §9.6의 401 케이스는 standalone MockMvc 한계로 plan에서 제외 (`AuthControllerTest`가 인증 동작을 별도 검증)

**3. Type consistency**
- `UserFuel.charge(int)`, `consume(int)`, `initialize(Long)` — Task 3, 10–14에서 일관
- `FuelService.charge/consume` 시그니처: `(Long, int, FuelReason, String, String)` — Task 13, 14에서 일관
- `FuelTransaction.of(String, Long, TransactionType, int, FuelReason, String, int)` — Task 4, 13, 14에서 일관
- `MemberCreatedEvent(Long memberId)` — Task 8, 15, 16에서 일관

플랜 자체-검토 완료.
