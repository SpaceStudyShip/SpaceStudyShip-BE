# 탐험(Exploration) 도메인 재구현 Implementation Plan (frontend 계약 정합)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 우주 탐험(행성→지역 트리)을 연료로 해금하는 도메인을, 프론트 게스트 시드와 1:1 일치하는 시드 + 진행 게이트 + INSUFFICIENT_FUEL 응답 보강으로 구현한다.

**Architecture:** SS-Study 모듈에 `exploration/` 패키지, Controller만 SS-Web. 마스터 노드(`ExplorationNode`)는 시드 전용 read-only, 유저 진행(`UserExploration`)은 행 존재=해금. 행성 클리어/진행도는 조회 시 파생. 해금은 `@Transactional`로 `FuelService.consume`와 동일 트랜잭션, UNIQUE(user_id,node_id)로 멱등성. INSUFFICIENT_FUEL은 `requiredFuel`/`currentFuel`을 본문에 동봉.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Lombok, JUnit5+Mockito+AssertJ, Testcontainers(Postgres), Flyway, springdoc.

**Spec:** `docs/superpowers/specs/2026-05-29-exploration-domain-design.md`
**프론트 시드 원본:** Flutter 레포 `lib/features/exploration/data/seed/exploration_seed_data.dart`

**공통 규칙:**
- 테스트: `./gradlew :SS-Study:test`, `./gradlew :SS-Web:test`, `./gradlew :SS-Common:test`. 단일: `--tests "FQCN"`.
- 테스트 환경 = Testcontainers + `ddl-auto=create-drop` (엔티티가 스키마 생성, Flyway 비활성). `members` FK는 엔티티에 매핑하지 않음(마이그레이션에만 존재).
- 커밋 형식: `탐험 도메인 구현 : {type} : {설명} #27`. 이슈번호 #27. **이모지 금지. Co-Authored-By 금지.**

---

## File Structure

**SS-Common**
- Modify: `.../common/exception/ErrorCode.java` — 탐험 에러 5종 추가
- Modify: `.../common/exception/ErrorResponse.java` — nullable `requiredFuel`/`currentFuel` + `@JsonInclude(NON_NULL)`
- Create: `.../common/exception/InsufficientFuelException.java`
- Modify: `.../common/exception/GlobalExceptionHandler.java` — `InsufficientFuelException` 핸들러

**SS-Study** (`.../study/exploration/`)
- `constant/NodeType.java`, `constant/NodeTypeConverter.java`
- `entity/ExplorationNode.java`, `entity/UserExploration.java`
- `repository/ExplorationNodeRepository.java`, `repository/UserExplorationRepository.java`
- `dto/` 6 records
- `service/ExplorationService.java`
- Modify: `SS-Study/src/test/.../study/StudyTestApplication.java` — repo 패키지 등록

**SS-Web**
- `controller/exploration/ExplorationController.java`

**리소스/문서**
- `SS-Web/src/main/resources/db/migration/V0_0_42__add_exploration.sql` — 스키마 + 시드 38노드
- Modify: `docs/api-specs/05_exploration.md`

---

## Task 0: Working tree 폐기 (clean 재시작)

**목적:** 이전 구현(프론트 계약과 어긋난)을 전부 제거하고 main 기준 clean 상태로 되돌린다. (복구 필요 시 reflog `87b0fc8`)

**Files:** (없음 — 정리 작업)

- [ ] **Step 1: 추적 파일 수정분 되돌리기**

```bash
cd /Users/luca/workspace/Java_Spring/space_study_ship
git checkout -- .
```

- [ ] **Step 2: 미추적 구현 파일/마이그레이션 삭제 (docs/superpowers는 보존)**

```bash
rm -rf SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration
rm -rf SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration
rm -rf SS-Web/src/main/java/com/elipair/spacestudyship/controller/exploration
rm -rf SS-Web/src/test/java/com/elipair/spacestudyship/controller/exploration
rm -f  SS-Web/src/main/resources/db/migration/V0_0_42__add_exploration.sql
```

- [ ] **Step 3: clean 상태 확인 (docs/superpowers/* 외에 변경 없어야 함)**

Run: `git status -s`
Expected: `docs/superpowers/specs/...` 및 `docs/superpowers/plans/...` (untracked)만 표시. exploration 관련 코드/마이그레이션 흔적 없음.

- [ ] **Step 4: clean 상태에서 전체 테스트 green 확인 (회귀 베이스라인)**

Run: `./gradlew :SS-Common:test :SS-Study:test :SS-Web:test`
Expected: BUILD SUCCESSFUL

커밋 없음(정리 단계).

---

## Task 1: 에러 인프라 (ErrorCode 5종 + ErrorResponse 보강 + InsufficientFuelException + 핸들러)

**Files:**
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java`
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorResponse.java`
- Create: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/InsufficientFuelException.java`
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/GlobalExceptionHandler.java`
- Test: `SS-Common/src/test/java/com/elipair/spacestudyship/common/exception/ErrorResponseTest.java`

- [ ] **Step 1: 실패 테스트 작성 (ErrorResponse 팩토리 + 예외 게터)**

```java
package com.elipair.spacestudyship.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    @DisplayName("of(ErrorCode): requiredFuel/currentFuel은 null")
    void of_basic_nullFuelFields() {
        ErrorResponse r = ErrorResponse.of(ErrorCode.PLANET_NOT_FOUND);
        assertThat(r.code()).isEqualTo("PLANET_NOT_FOUND");
        assertThat(r.requiredFuel()).isNull();
        assertThat(r.currentFuel()).isNull();
    }

    @Test
    @DisplayName("ofInsufficientFuel: 연료 수치 포함")
    void ofInsufficientFuel_includesAmounts() {
        ErrorResponse r = ErrorResponse.ofInsufficientFuel("연료가 부족합니다.", 10, 4);
        assertThat(r.code()).isEqualTo("INSUFFICIENT_FUEL");
        assertThat(r.requiredFuel()).isEqualTo(10);
        assertThat(r.currentFuel()).isEqualTo(4);
    }

    @Test
    @DisplayName("InsufficientFuelException: 게터로 수치 노출")
    void exception_getters() {
        InsufficientFuelException ex = new InsufficientFuelException(10, 4);
        assertThat(ex.getRequiredFuel()).isEqualTo(10);
        assertThat(ex.getCurrentFuel()).isEqualTo(4);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :SS-Common:test --tests "com.elipair.spacestudyship.common.exception.ErrorResponseTest"`
Expected: FAIL — `ofInsufficientFuel` / `InsufficientFuelException` 없음(컴파일 에러)

- [ ] **Step 3: ErrorCode에 5종 추가**

`ErrorCode.java`에서 `// Timer` 블록 뒤(또는 `// Common` 앞)에 추가:

```java
    // Exploration
    PLANET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 행성을 찾을 수 없습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 지역을 찾을 수 없습니다."),
    ALREADY_UNLOCKED(HttpStatus.BAD_REQUEST, "이미 해금된 노드입니다."),
    PLANET_LOCKED(HttpStatus.BAD_REQUEST, "상위 행성이 아직 해금되지 않았습니다."),
    PREREQUISITE_NOT_CLEARED(HttpStatus.BAD_REQUEST, "이전 행성을 먼저 클리어해야 합니다."),
```

- [ ] **Step 4: ErrorResponse 보강**

`ErrorResponse.java` 전체를 아래로 교체:

```java
package com.elipair.spacestudyship.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Integer requiredFuel,
        Integer currentFuel
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, null, null);
    }

    public static ErrorResponse ofInsufficientFuel(String message, int requiredFuel, int currentFuel) {
        return new ErrorResponse(ErrorCode.INSUFFICIENT_FUEL.name(), message, requiredFuel, currentFuel);
    }
}
```

- [ ] **Step 5: InsufficientFuelException 생성**

```java
package com.elipair.spacestudyship.common.exception;

import lombok.Getter;

@Getter
public class InsufficientFuelException extends RuntimeException {

    private final int requiredFuel;
    private final int currentFuel;

    public InsufficientFuelException(int requiredFuel, int currentFuel) {
        super(ErrorCode.INSUFFICIENT_FUEL.getMessage());
        this.requiredFuel = requiredFuel;
        this.currentFuel = currentFuel;
    }
}
```

- [ ] **Step 6: GlobalExceptionHandler에 핸들러 추가**

`GlobalExceptionHandler.java`의 `handleCustomException` 메서드 바로 뒤에 추가:

```java
    @ExceptionHandler(InsufficientFuelException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFuel(InsufficientFuelException ex) {
        log.info("[Exception] 연료 부족 | required={}, current={}", ex.getRequiredFuel(), ex.getCurrentFuel());
        return ResponseEntity
                .status(ErrorCode.INSUFFICIENT_FUEL.getHttpStatus())
                .body(ErrorResponse.ofInsufficientFuel(
                        ErrorCode.INSUFFICIENT_FUEL.getMessage(),
                        ex.getRequiredFuel(), ex.getCurrentFuel()));
    }
```

- [ ] **Step 7: 테스트 통과 확인 + 회귀 확인**

Run: `./gradlew :SS-Common:test`
Expected: PASS (신규 ErrorResponseTest 포함, 기존 회귀 없음)

- [ ] **Step 8: Commit**

```bash
git add SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ SS-Common/src/test/java/com/elipair/spacestudyship/common/exception/ErrorResponseTest.java
git commit -m "탐험 도메인 구현 : feat : 탐험 ErrorCode 5종 + INSUFFICIENT_FUEL 응답 보강 #27"
```

---

## Task 2: NodeType enum + Converter

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/constant/NodeType.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/constant/NodeTypeConverter.java`

- [ ] **Step 1: NodeType enum**

```java
package com.elipair.spacestudyship.study.exploration.constant;

public enum NodeType {
    PLANET,
    REGION;

    /** DB 컬럼/JSON 직렬화용 소문자 표현 ("planet" / "region"). */
    public String value() {
        return name().toLowerCase();
    }

    public static NodeType from(String value) {
        return NodeType.valueOf(value.toUpperCase());
    }
}
```

- [ ] **Step 2: Converter**

```java
package com.elipair.spacestudyship.study.exploration.constant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NodeTypeConverter implements AttributeConverter<NodeType, String> {

    @Override
    public String convertToDatabaseColumn(NodeType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NodeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NodeType.from(dbData);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :SS-Study:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/constant/
git commit -m "탐험 도메인 구현 : feat : NodeType enum + Converter 추가 #27"
```

---

## Task 3: ExplorationNode 엔티티 (마스터)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/entity/ExplorationNode.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/entity/ExplorationNodeTest.java`

- [ ] **Step 1: 실패 테스트**

```java
package com.elipair.spacestudyship.study.exploration.entity;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorationNodeTest {

    @Test
    @DisplayName("planet 빌더: 필드 매핑")
    void buildsPlanet() {
        ExplorationNode node = ExplorationNode.builder()
                .id("earth").name("지구").nodeType(NodeType.PLANET).depth(2)
                .icon("earth").parentId(null).prerequisiteNodeId(null)
                .requiredFuel(0).sortOrder(0).description("시작점")
                .mapX(0.5).mapY(0.08).build();

        assertThat(node.getId()).isEqualTo("earth");
        assertThat(node.getNodeType()).isEqualTo(NodeType.PLANET);
        assertThat(node.getRequiredFuel()).isZero();
        assertThat(node.getParentId()).isNull();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.entity.ExplorationNodeTest"`
Expected: FAIL — 클래스 없음

- [ ] **Step 3: 엔티티 구현 (BaseTimeEntity 미상속)**

```java
package com.elipair.spacestudyship.study.exploration.entity;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.constant.NodeTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exploration_nodes")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorationNode {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Convert(converter = NodeTypeConverter.class)
    @Column(name = "node_type", nullable = false, length = 10)
    private NodeType nodeType;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false, length = 30)
    private String icon;

    @Column(name = "parent_id", length = 50)
    private String parentId;

    @Column(name = "prerequisite_node_id", length = 50)
    private String prerequisiteNodeId;

    @Column(name = "required_fuel", nullable = false)
    private int requiredFuel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "map_x", nullable = false)
    private double mapX;

    @Column(name = "map_y", nullable = false)
    private double mapY;
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.entity.ExplorationNodeTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/entity/ExplorationNode.java SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/entity/ExplorationNodeTest.java
git commit -m "탐험 도메인 구현 : feat : ExplorationNode 마스터 엔티티 추가 #27"
```

---

## Task 4: UserExploration 엔티티

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/entity/UserExploration.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/entity/UserExplorationTest.java`

- [ ] **Step 1: 실패 테스트**

```java
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.entity.UserExplorationTest"`
Expected: FAIL — 클래스 없음

- [ ] **Step 3: 엔티티 구현**

```java
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
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.entity.UserExplorationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/entity/UserExploration.java SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/entity/UserExplorationTest.java
git commit -m "탐험 도메인 구현 : feat : UserExploration 진행 엔티티 추가 #27"
```

---

## Task 5: Repository 2종 + 테스트

**Files:**
- Create: `.../exploration/repository/ExplorationNodeRepository.java`
- Create: `.../exploration/repository/UserExplorationRepository.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java`
- Test: `.../exploration/repository/ExplorationNodeRepositoryTest.java`, `UserExplorationRepositoryTest.java`

- [ ] **Step 1: Repository 인터페이스**

`ExplorationNodeRepository.java`:

```java
package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExplorationNodeRepository extends JpaRepository<ExplorationNode, String> {

    List<ExplorationNode> findByNodeTypeOrderBySortOrderAsc(NodeType nodeType);

    List<ExplorationNode> findByParentIdOrderBySortOrderAsc(String parentId);
}
```

`UserExplorationRepository.java`:

```java
package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserExplorationRepository extends JpaRepository<UserExploration, Long> {

    List<UserExploration> findByUserId(Long userId);

    boolean existsByUserIdAndNodeId(Long userId, String nodeId);
}
```

- [ ] **Step 2: StudyTestApplication 패키지 등록**

`@EnableJpaRepositories` basePackages 배열에 추가:

```java
@EnableJpaRepositories(basePackages = {
        "com.elipair.spacestudyship.study.todo.repository",
        "com.elipair.spacestudyship.study.fuel.repository",
        "com.elipair.spacestudyship.study.timer.repository",
        "com.elipair.spacestudyship.study.exploration.repository"
})
```

- [ ] **Step 3: 실패 테스트 — ExplorationNodeRepositoryTest**

```java
package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class ExplorationNodeRepositoryTest {

    @Autowired
    ExplorationNodeRepository nodeRepository;

    private ExplorationNode planet(String id, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.PLANET)
                .depth(2).icon(id).requiredFuel(0).sortOrder(sort)
                .description("").mapX(0).mapY(0).build();
    }

    private ExplorationNode region(String id, String parent, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.REGION)
                .depth(3).icon(id).parentId(parent).requiredFuel(1).sortOrder(sort)
                .description("").mapX(0).mapY(0).build();
    }

    @Test
    @DisplayName("findByNodeTypeOrderBySortOrderAsc: 타입 필터 + 정렬")
    void findByNodeType_sorted() {
        nodeRepository.saveAll(List.of(planet("b", 1), planet("a", 0)));
        nodeRepository.saveAll(List.of(region("r1", "a", 0)));

        List<ExplorationNode> planets = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET);

        assertThat(planets).extracting(ExplorationNode::getId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("findByParentIdOrderBySortOrderAsc: 부모별 정렬 조회")
    void findByParent_sorted() {
        nodeRepository.save(planet("a", 0));
        nodeRepository.saveAll(List.of(region("r2", "a", 1), region("r1", "a", 0)));

        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc("a");

        assertThat(regions).extracting(ExplorationNode::getId).containsExactly("r1", "r2");
    }
}
```

- [ ] **Step 4: 실패 테스트 — UserExplorationRepositoryTest**

```java
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
```

- [ ] **Step 5: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.repository.*"`
Expected: FAIL — Repository 미존재(컴파일 에러)

- [ ] **Step 6: 통과 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.repository.*"`
Expected: PASS (2 클래스)

- [ ] **Step 7: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/repository/ SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/repository/ SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java
git commit -m "탐험 도메인 구현 : feat : Exploration Repository 2종 + 테스트 #27"
```

---

## Task 6: DTO 6종

**Files:** (모두 `SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/dto/`)

- [ ] **Step 1: ProgressDto**

```java
package com.elipair.spacestudyship.study.exploration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행성 진행도")
public record ProgressDto(
        @Schema(example = "3") int clearedChildren,
        @Schema(example = "5") int totalChildren,
        @Schema(example = "0.6") double progressRatio
) {}
```

- [ ] **Step 2: PlanetResponse**

```java
package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "행성 응답")
public record PlanetResponse(
        String id, String name, String nodeType, int depth, String icon,
        @Schema(nullable = true) String parentId,
        @Schema(nullable = true) String prerequisiteId,
        int requiredFuel, boolean isUnlocked, boolean isCleared, int sortOrder,
        String description, double mapX, double mapY,
        @Schema(nullable = true, example = "2026-04-01T00:00:00Z") String unlockedAt,
        ProgressDto progress
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static PlanetResponse of(ExplorationNode n, boolean isUnlocked, boolean isCleared,
                                    int clearedChildren, int totalChildren, double progressRatio,
                                    LocalDateTime unlockedAt) {
        return new PlanetResponse(
                n.getId(), n.getName(), n.getNodeType().value(), n.getDepth(), n.getIcon(),
                n.getParentId(), n.getPrerequisiteNodeId(), n.getRequiredFuel(),
                isUnlocked, isCleared, n.getSortOrder(), n.getDescription(), n.getMapX(), n.getMapY(),
                formatUtc(unlockedAt),
                new ProgressDto(clearedChildren, totalChildren, progressRatio));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 3: RegionResponse**

```java
package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "지역 응답")
public record RegionResponse(
        String id, String name, String nodeType, int depth, String icon,
        @Schema(nullable = true) String parentId,
        int requiredFuel, boolean isUnlocked, boolean isCleared, int sortOrder,
        String description, double mapX, double mapY,
        @Schema(nullable = true, example = "2026-04-05T15:30:00Z") String unlockedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static RegionResponse of(ExplorationNode n, boolean isUnlocked, boolean isCleared,
                                    LocalDateTime unlockedAt) {
        return new RegionResponse(
                n.getId(), n.getName(), n.getNodeType().value(), n.getDepth(), n.getIcon(),
                n.getParentId(), n.getRequiredFuel(), isUnlocked, isCleared,
                n.getSortOrder(), n.getDescription(), n.getMapX(), n.getMapY(),
                formatUtc(unlockedAt));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 4: UnlockedNodeDto**

```java
package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "해금된 노드 요약")
public record UnlockedNodeDto(
        String id, String name, boolean isUnlocked, boolean isCleared,
        @Schema(example = "2026-04-16T11:00:00Z") String unlockedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static UnlockedNodeDto of(ExplorationNode node, UserExploration progress, boolean cleared) {
        return new UnlockedNodeDto(
                node.getId(), node.getName(), true, cleared,
                formatUtc(progress.getUnlockedAt()));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 5: RegionUnlockResponse + PlanetUnlockResponse**

`RegionUnlockResponse.java`:

```java
package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지역 해금 응답")
public record RegionUnlockResponse(
        UnlockedNodeDto region, int fuelConsumed, int currentFuel, boolean planetCleared
) {
    public static RegionUnlockResponse of(ExplorationNode region, UserExploration progress,
                                          int fuelConsumed, int currentFuel, boolean planetCleared) {
        return new RegionUnlockResponse(
                UnlockedNodeDto.of(region, progress, true),
                fuelConsumed, currentFuel, planetCleared);
    }
}
```

`PlanetUnlockResponse.java`:

```java
package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행성 해금 응답")
public record PlanetUnlockResponse(
        UnlockedNodeDto planet, int fuelConsumed, int currentFuel
) {
    public static PlanetUnlockResponse of(ExplorationNode planet, UserExploration progress,
                                          int fuelConsumed, int currentFuel) {
        return new PlanetUnlockResponse(
                UnlockedNodeDto.of(planet, progress, false),
                fuelConsumed, currentFuel);
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew :SS-Study:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/dto/
git commit -m "탐험 도메인 구현 : feat : Exploration DTO 6종 추가 #27"
```

---

## Task 7: ExplorationService — 골격 + 목록 조회 2개

**Files:**
- Create: `.../exploration/service/ExplorationService.java`
- Test: `.../exploration/service/ExplorationServiceTest.java`

> Mockito 단위 테스트(`@ExtendWith(MockitoExtension.class)`, repo + FuelService mock).

- [ ] **Step 1: 실패 테스트**

```java
package com.elipair.spacestudyship.study.exploration.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import com.elipair.spacestudyship.study.exploration.repository.ExplorationNodeRepository;
import com.elipair.spacestudyship.study.exploration.repository.UserExplorationRepository;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExplorationServiceTest {

    @Mock ExplorationNodeRepository nodeRepository;
    @Mock UserExplorationRepository userExplorationRepository;
    @Mock FuelService fuelService;
    @InjectMocks ExplorationService service;

    private ExplorationNode planet(String id, int requiredFuel, String prereq, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.PLANET).depth(2)
                .icon(id).parentId(null).prerequisiteNodeId(prereq)
                .requiredFuel(requiredFuel).sortOrder(sort).description("").mapX(0).mapY(0).build();
    }

    private ExplorationNode region(String id, String parent, int requiredFuel, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.REGION).depth(3)
                .icon(id).parentId(parent).prerequisiteNodeId(null)
                .requiredFuel(requiredFuel).sortOrder(sort).description("").mapX(0).mapY(0).build();
    }

    @Test
    @DisplayName("getPlanets: earth는 requiredFuel=0이라 암묵 해금, 진행도 파생")
    void getPlanets_derivesUnlockAndProgress() {
        given(nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET))
                .willReturn(List.of(planet("earth", 0, null, 0), planet("mercury", 3, "earth", 1)));
        given(nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.REGION))
                .willReturn(List.of(region("korea", "earth", 0, 0),
                        region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));

        List<PlanetResponse> result = service.getPlanets(1L);

        PlanetResponse earth = result.get(0);
        assertThat(earth.id()).isEqualTo("earth");
        assertThat(earth.isUnlocked()).isTrue();
        assertThat(earth.isCleared()).isFalse();
        assertThat(earth.progress().clearedChildren()).isEqualTo(1);
        assertThat(earth.progress().totalChildren()).isEqualTo(2);
        assertThat(earth.progress().progressRatio()).isEqualTo(0.5);

        PlanetResponse mercury = result.get(1);
        assertThat(mercury.isUnlocked()).isFalse();
        assertThat(mercury.prerequisiteId()).isEqualTo("earth");
    }

    @Test
    @DisplayName("getRegions: 행성 없으면 PLANET_NOT_FOUND")
    void getRegions_planetNotFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRegions(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("getRegions: 해금된 지역 isUnlocked/isCleared=true, korea(연료0) 암묵 해금")
    void getRegions_mapsUnlock() {
        given(nodeRepository.findById("earth")).willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0),
                        region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L)).willReturn(List.of());

        List<RegionResponse> result = service.getRegions(1L, "earth");

        assertThat(result).extracting(RegionResponse::id).containsExactly("korea", "japan");
        assertThat(result.get(0).isUnlocked()).isTrue();   // korea requiredFuel=0 → 암묵 해금
        assertThat(result.get(0).isCleared()).isTrue();
        assertThat(result.get(1).isUnlocked()).isFalse();  // japan 미해금
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.service.ExplorationServiceTest"`
Expected: FAIL — `ExplorationService` 없음

- [ ] **Step 3: 서비스 구현 (조회 2개 + private 헬퍼)**

```java
package com.elipair.spacestudyship.study.exploration.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import com.elipair.spacestudyship.study.exploration.repository.ExplorationNodeRepository;
import com.elipair.spacestudyship.study.exploration.repository.UserExplorationRepository;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplorationService {

    private final ExplorationNodeRepository nodeRepository;
    private final UserExplorationRepository userExplorationRepository;
    private final FuelService fuelService;

    public List<PlanetResponse> getPlanets(Long userId) {
        List<ExplorationNode> planets = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET);
        List<ExplorationNode> regions = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.REGION);
        Map<String, UserExploration> progress = progressMap(userId);
        Set<String> unlocked = progress.keySet();

        Map<String, Long> totalByParent = regions.stream()
                .collect(Collectors.groupingBy(ExplorationNode::getParentId, Collectors.counting()));
        Map<String, Long> clearedByParent = regions.stream()
                .filter(r -> unlocked.contains(r.getId()))
                .collect(Collectors.groupingBy(ExplorationNode::getParentId, Collectors.counting()));

        return planets.stream().map(p -> {
            int total = totalByParent.getOrDefault(p.getId(), 0L).intValue();
            int cleared = clearedByParent.getOrDefault(p.getId(), 0L).intValue();
            boolean isUnlocked = p.getRequiredFuel() == 0 || unlocked.contains(p.getId());
            boolean isCleared = total > 0 && cleared == total;
            double ratio = total == 0 ? 0.0 : (double) cleared / total;
            LocalDateTime unlockedAt = progress.containsKey(p.getId())
                    ? progress.get(p.getId()).getUnlockedAt() : null;
            return PlanetResponse.of(p, isUnlocked, isCleared, cleared, total, ratio, unlockedAt);
        }).toList();
    }

    public List<RegionResponse> getRegions(Long userId, String planetId) {
        nodeRepository.findById(planetId)
                .filter(n -> n.getNodeType() == NodeType.PLANET)
                .orElseThrow(() -> new CustomException(ErrorCode.PLANET_NOT_FOUND));

        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc(planetId);
        Map<String, UserExploration> progress = progressMap(userId);

        return regions.stream().map(r -> {
            UserExploration pr = progress.get(r.getId());
            boolean isUnlocked = r.getRequiredFuel() == 0 || pr != null;
            LocalDateTime unlockedAt = pr == null ? null : pr.getUnlockedAt();
            return RegionResponse.of(r, isUnlocked, isUnlocked, unlockedAt);
        }).toList();
    }

    private Map<String, UserExploration> progressMap(Long userId) {
        return userExplorationRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserExploration::getNodeId, Function.identity()));
    }

    private boolean isPlanetCleared(Long userId, String planetId) {
        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc(planetId);
        if (regions.isEmpty()) {
            return false;
        }
        Set<String> unlocked = userExplorationRepository.findByUserId(userId).stream()
                .map(UserExploration::getNodeId).collect(Collectors.toSet());
        return regions.stream().allMatch(r -> unlocked.contains(r.getId()));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.service.ExplorationServiceTest"`
Expected: PASS (3 테스트)

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/service/ExplorationService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/service/ExplorationServiceTest.java
git commit -m "탐험 도메인 구현 : feat : ExplorationService 목록 조회 2종 #27"
```

---

## Task 8: ExplorationService — 지역 해금 (+ 잔량 pre-check)

**Files:**
- Modify: `.../exploration/service/ExplorationService.java`
- Modify: `.../exploration/service/ExplorationServiceTest.java`

- [ ] **Step 1: 실패 테스트 추가**

import 추가:

```java
import com.elipair.spacestudyship.common.exception.InsufficientFuelException;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
```

테스트 추가:

```java
    private FuelResponse fuel(int currentFuel) {
        return new FuelResponse(currentFuel, 0, 0, 0, null);
    }

    private FuelTransactionResponse tx(int amount, int balanceAfter) {
        return new FuelTransactionResponse(
                "tx", "consume", amount, "EXPLORATION_UNLOCK", "ref", balanceAfter, null);
    }

    @Test
    @DisplayName("unlockRegion: 정상 해금 — 잔량충분 + 차감 + 저장 + 마지막 지역이면 planetCleared=true")
    void unlockRegion_success_lastRegionClearsPlanet() {
        given(nodeRepository.findById("japan"))
                .willReturn(Optional.of(region("japan", "earth", 1, 1)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "japan")).willReturn(false);
        given(fuelService.getFuel(1L)).willReturn(fuel(250));
        given(fuelService.consume(eq(1L), eq(1), eq(FuelReason.EXPLORATION_UNLOCK), eq("japan"), anyString()))
                .willReturn(tx(1, 249));
        given(userExplorationRepository.save(any(UserExploration.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0), region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true),
                        UserExploration.unlock(1L, "japan", true)));

        var result = service.unlockRegion(1L, "japan");

        assertThat(result.region().id()).isEqualTo("japan");
        assertThat(result.region().isCleared()).isTrue();
        assertThat(result.fuelConsumed()).isEqualTo(1);
        assertThat(result.currentFuel()).isEqualTo(249);
        assertThat(result.planetCleared()).isTrue();

        ArgumentCaptor<UserExploration> captor = ArgumentCaptor.forClass(UserExploration.class);
        verify(userExplorationRepository).save(captor.capture());
        assertThat(captor.getValue().getNodeId()).isEqualTo("japan");
        assertThat(captor.getValue().isCleared()).isTrue();
    }

    @Test
    @DisplayName("unlockRegion: 잔량 부족 → InsufficientFuelException + consume 미호출")
    void unlockRegion_insufficientFuel() {
        given(nodeRepository.findById("usa"))
                .willReturn(Optional.of(region("usa", "earth", 3, 8)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "usa")).willReturn(false);
        given(fuelService.getFuel(1L)).willReturn(fuel(1));

        assertThatThrownBy(() -> service.unlockRegion(1L, "usa"))
                .isInstanceOf(InsufficientFuelException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 부모 행성 미해금 → PLANET_LOCKED")
    void unlockRegion_parentLocked() {
        given(nodeRepository.findById("mars_olympus"))
                .willReturn(Optional.of(region("mars_olympus", "mars", 3, 0)));
        given(nodeRepository.findById("mars"))
                .willReturn(Optional.of(planet("mars", 10, "venus", 3)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mars")).willReturn(false);

        assertThatThrownBy(() -> service.unlockRegion(1L, "mars_olympus"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 이미 해금 → ALREADY_UNLOCKED")
    void unlockRegion_alreadyUnlocked() {
        given(nodeRepository.findById("japan"))
                .willReturn(Optional.of(region("japan", "earth", 1, 1)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "japan")).willReturn(true);

        assertThatThrownBy(() -> service.unlockRegion(1L, "japan"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 없는 지역 → REGION_NOT_FOUND")
    void unlockRegion_notFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlockRegion(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.service.ExplorationServiceTest"`
Expected: FAIL — `unlockRegion` 없음

- [ ] **Step 3: 서비스에 import + unlockRegion 추가**

import 추가:

```java
import com.elipair.spacestudyship.common.exception.InsufficientFuelException;
import com.elipair.spacestudyship.study.exploration.dto.RegionUnlockResponse;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;

import java.util.UUID;
```

메서드 추가:

```java
    @Transactional
    public RegionUnlockResponse unlockRegion(Long userId, String regionId) {
        ExplorationNode region = nodeRepository.findById(regionId)
                .filter(n -> n.getNodeType() == NodeType.REGION)
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        ExplorationNode parent = nodeRepository.findById(region.getParentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PLANET_NOT_FOUND));
        boolean parentUnlocked = parent.getRequiredFuel() == 0
                || userExplorationRepository.existsByUserIdAndNodeId(userId, parent.getId());
        if (!parentUnlocked) {
            throw new CustomException(ErrorCode.PLANET_LOCKED);
        }

        if (region.getRequiredFuel() == 0
                || userExplorationRepository.existsByUserIdAndNodeId(userId, regionId)) {
            throw new CustomException(ErrorCode.ALREADY_UNLOCKED);
        }

        requireFuel(userId, region.getRequiredFuel());

        FuelTransactionResponse fuelTx = fuelService.consume(
                userId, region.getRequiredFuel(), FuelReason.EXPLORATION_UNLOCK,
                regionId, UUID.randomUUID().toString());

        UserExploration saved = userExplorationRepository.save(
                UserExploration.unlock(userId, regionId, true));

        boolean planetCleared = isPlanetCleared(userId, parent.getId());

        log.info("[Exploration] 지역 해금 | userId={}, regionId={}, fuel={}, planetCleared={}",
                userId, regionId, region.getRequiredFuel(), planetCleared);

        return RegionUnlockResponse.of(region, saved,
                fuelTx.amount(), fuelTx.balanceAfter(), planetCleared);
    }

    private void requireFuel(Long userId, int requiredFuel) {
        int currentFuel = fuelService.getFuel(userId).currentFuel();
        if (currentFuel < requiredFuel) {
            throw new InsufficientFuelException(requiredFuel, currentFuel);
        }
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.service.ExplorationServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/service/ExplorationService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/service/ExplorationServiceTest.java
git commit -m "탐험 도메인 구현 : feat : 지역 해금 로직 + 잔량 pre-check + 자동 클리어 #27"
```

---

## Task 9: ExplorationService — 행성 해금 (선행 게이트)

**Files:**
- Modify: `.../exploration/service/ExplorationService.java`
- Modify: `.../exploration/service/ExplorationServiceTest.java`

- [ ] **Step 1: 실패 테스트 추가**

```java
    @Test
    @DisplayName("unlockPlanet: 선행 행성 클리어 시 정상 해금")
    void unlockPlanet_success() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));
        given(fuelService.getFuel(1L)).willReturn(fuel(100));
        given(fuelService.consume(eq(1L), eq(3), eq(FuelReason.EXPLORATION_UNLOCK), eq("mercury"), anyString()))
                .willReturn(tx(3, 97));
        given(userExplorationRepository.save(any(UserExploration.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = service.unlockPlanet(1L, "mercury");

        assertThat(result.planet().id()).isEqualTo("mercury");
        assertThat(result.planet().isCleared()).isFalse();
        assertThat(result.fuelConsumed()).isEqualTo(3);
        assertThat(result.currentFuel()).isEqualTo(97);
    }

    @Test
    @DisplayName("unlockPlanet: 선행 미클리어 → PREREQUISITE_NOT_CLEARED + consume 미호출")
    void unlockPlanet_prerequisiteNotCleared() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0), region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true))); // 1/2만

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 잔량 부족 → InsufficientFuelException + consume 미호출")
    void unlockPlanet_insufficientFuel() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));
        given(fuelService.getFuel(1L)).willReturn(fuel(1));

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(InsufficientFuelException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 이미 해금 → ALREADY_UNLOCKED")
    void unlockPlanet_alreadyUnlocked() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(true);

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 없는 행성 → PLANET_NOT_FOUND")
    void unlockPlanet_notFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlockPlanet(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.service.ExplorationServiceTest"`
Expected: FAIL — `unlockPlanet` 없음

- [ ] **Step 3: import + unlockPlanet 추가**

import 추가:

```java
import com.elipair.spacestudyship.study.exploration.dto.PlanetUnlockResponse;
```

메서드 추가:

```java
    @Transactional
    public PlanetUnlockResponse unlockPlanet(Long userId, String planetId) {
        ExplorationNode planet = nodeRepository.findById(planetId)
                .filter(n -> n.getNodeType() == NodeType.PLANET)
                .orElseThrow(() -> new CustomException(ErrorCode.PLANET_NOT_FOUND));

        if (planet.getRequiredFuel() == 0
                || userExplorationRepository.existsByUserIdAndNodeId(userId, planetId)) {
            throw new CustomException(ErrorCode.ALREADY_UNLOCKED);
        }

        if (planet.getPrerequisiteNodeId() != null
                && !isPlanetCleared(userId, planet.getPrerequisiteNodeId())) {
            throw new CustomException(ErrorCode.PREREQUISITE_NOT_CLEARED);
        }

        requireFuel(userId, planet.getRequiredFuel());

        FuelTransactionResponse fuelTx = fuelService.consume(
                userId, planet.getRequiredFuel(), FuelReason.EXPLORATION_UNLOCK,
                planetId, UUID.randomUUID().toString());

        UserExploration saved = userExplorationRepository.save(
                UserExploration.unlock(userId, planetId, false));

        log.info("[Exploration] 행성 해금 | userId={}, planetId={}, fuel={}",
                userId, planetId, planet.getRequiredFuel());

        return PlanetUnlockResponse.of(planet, saved, fuelTx.amount(), fuelTx.balanceAfter());
    }
```

- [ ] **Step 4: 통과 확인 (서비스 전체)**

Run: `./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.exploration.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/exploration/service/ExplorationService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/exploration/service/ExplorationServiceTest.java
git commit -m "탐험 도메인 구현 : feat : 행성 해금 로직 + 선행 클리어 게이트 #27"
```

---

## Task 10: ExplorationController + MockMvc 테스트

**Files:**
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/exploration/ExplorationController.java`
- Test: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/exploration/ExplorationControllerTest.java`

- [ ] **Step 1: 실패 테스트**

```java
package com.elipair.spacestudyship.controller.exploration;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.common.exception.InsufficientFuelException;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.PlanetUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.UnlockedNodeDto;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.service.ExplorationService;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExplorationControllerTest {

    @Mock ExplorationService explorationService;
    @InjectMocks ExplorationController controller;

    MockMvc mockMvc;

    private ExplorationNode planetNode() {
        return ExplorationNode.builder().id("earth").name("지구").nodeType(NodeType.PLANET)
                .depth(2).icon("earth").requiredFuel(0).sortOrder(0)
                .description("시작점").mapX(0.5).mapY(0.08).build();
    }

    private ExplorationNode regionNode() {
        return ExplorationNode.builder().id("korea").name("대한민국").nodeType(NodeType.REGION)
                .depth(3).icon("KR").parentId("earth").requiredFuel(0).sortOrder(0)
                .description("한반도").mapX(0).mapY(0).build();
    }

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .build();
    }

    @Test
    @DisplayName("GET /api/explorations/planets — 200, nodeType 소문자")
    void getPlanets_200() throws Exception {
        given(explorationService.getPlanets(1L)).willReturn(List.of(
                PlanetResponse.of(planetNode(), true, false, 1, 2, 0.5, null)));

        mockMvc.perform(get("/api/explorations/planets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("earth"))
                .andExpect(jsonPath("$[0].nodeType").value("planet"))
                .andExpect(jsonPath("$[0].isUnlocked").value(true))
                .andExpect(jsonPath("$[0].progress.totalChildren").value(2));
    }

    @Test
    @DisplayName("GET /api/explorations/planets/{id}/regions — 200")
    void getRegions_200() throws Exception {
        given(explorationService.getRegions(1L, "earth")).willReturn(List.of(
                RegionResponse.of(regionNode(), true, true, null)));

        mockMvc.perform(get("/api/explorations/planets/earth/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("korea"))
                .andExpect(jsonPath("$[0].nodeType").value("region"));
    }

    @Test
    @DisplayName("GET regions — 행성 없음 404 PLANET_NOT_FOUND")
    void getRegions_404() throws Exception {
        given(explorationService.getRegions(1L, "nope"))
                .willThrow(new CustomException(ErrorCode.PLANET_NOT_FOUND));

        mockMvc.perform(get("/api/explorations/planets/nope/regions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANET_NOT_FOUND"))
                .andExpect(jsonPath("$.requiredFuel").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/explorations/regions/{id}/unlock — 200")
    void unlockRegion_200() throws Exception {
        given(explorationService.unlockRegion(1L, "japan")).willReturn(
                new RegionUnlockResponse(
                        new UnlockedNodeDto("japan", "일본", true, true, "2026-04-16T11:00:00Z"),
                        1, 249, false));

        mockMvc.perform(post("/api/explorations/regions/japan/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region.id").value("japan"))
                .andExpect(jsonPath("$.fuelConsumed").value(1))
                .andExpect(jsonPath("$.currentFuel").value(249))
                .andExpect(jsonPath("$.planetCleared").value(false));
    }

    @Test
    @DisplayName("POST region unlock — 연료 부족 400 + requiredFuel/currentFuel 본문")
    void unlockRegion_insufficientFuel_400() throws Exception {
        willThrow(new InsufficientFuelException(3, 1))
                .given(explorationService).unlockRegion(1L, "usa");

        mockMvc.perform(post("/api/explorations/regions/usa/unlock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUEL"))
                .andExpect(jsonPath("$.requiredFuel").value(3))
                .andExpect(jsonPath("$.currentFuel").value(1));
    }

    @Test
    @DisplayName("POST /api/explorations/planets/{id}/unlock — 200")
    void unlockPlanet_200() throws Exception {
        given(explorationService.unlockPlanet(1L, "mercury")).willReturn(
                new PlanetUnlockResponse(
                        new UnlockedNodeDto("mercury", "수성", true, false, "2026-04-16T11:30:00Z"),
                        3, 97));

        mockMvc.perform(post("/api/explorations/planets/mercury/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planet.id").value("mercury"))
                .andExpect(jsonPath("$.fuelConsumed").value(3))
                .andExpect(jsonPath("$.currentFuel").value(97));
    }

    @Test
    @DisplayName("POST planet unlock — 선행 미클리어 400 PREREQUISITE_NOT_CLEARED")
    void unlockPlanet_prerequisite_400() throws Exception {
        willThrow(new CustomException(ErrorCode.PREREQUISITE_NOT_CLEARED))
                .given(explorationService).unlockPlanet(1L, "mercury");

        mockMvc.perform(post("/api/explorations/planets/mercury/unlock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_CLEARED"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.exploration.ExplorationControllerTest"`
Expected: FAIL — `ExplorationController` 없음

- [ ] **Step 3: 컨트롤러 구현**

```java
package com.elipair.spacestudyship.controller.exploration;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.PlanetUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionUnlockResponse;
import com.elipair.spacestudyship.study.exploration.service.ExplorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Exploration", description = "우주 탐험(행성/지역 해금) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/explorations")
public class ExplorationController {

    private final ExplorationService explorationService;

    @Operation(summary = "행성 목록 조회",
            description = "전체 행성 목록과 유저의 해금/클리어 상태, 진행도를 반환합니다. 정렬: sortOrder 오름차순.")
    @GetMapping("/planets")
    public ResponseEntity<List<PlanetResponse>> getPlanets(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(explorationService.getPlanets(loginMember.memberId()));
    }

    @Operation(summary = "행성 하위 지역 목록 조회",
            description = "특정 행성의 하위 지역과 유저 해금 상태를 반환합니다. 행성이 없으면 404 PLANET_NOT_FOUND.")
    @GetMapping("/planets/{planetId}/regions")
    public ResponseEntity<List<RegionResponse>> getRegions(
            @AuthMember LoginMember loginMember,
            @PathVariable String planetId) {
        return ResponseEntity.ok(explorationService.getRegions(loginMember.memberId(), planetId));
    }

    @Operation(summary = "지역 해금",
            description = """
                연료를 소비하여 지역을 해금합니다(해금=클리어). 잔량 확인+차감+해금을 원자적으로 처리합니다.
                상위 행성의 모든 지역이 해금되면 planetCleared=true.

                에러: 400 INSUFFICIENT_FUEL(requiredFuel/currentFuel 동봉) / ALREADY_UNLOCKED / PLANET_LOCKED, 404 REGION_NOT_FOUND
                """)
    @PostMapping("/regions/{regionId}/unlock")
    public ResponseEntity<RegionUnlockResponse> unlockRegion(
            @AuthMember LoginMember loginMember,
            @PathVariable String regionId) {
        return ResponseEntity.ok(explorationService.unlockRegion(loginMember.memberId(), regionId));
    }

    @Operation(summary = "행성 해금",
            description = """
                연료를 소비하여 행성을 해금합니다. 선행 행성을 클리어해야 해금할 수 있습니다.

                에러: 400 INSUFFICIENT_FUEL(requiredFuel/currentFuel 동봉) / ALREADY_UNLOCKED / PREREQUISITE_NOT_CLEARED, 404 PLANET_NOT_FOUND
                """)
    @PostMapping("/planets/{planetId}/unlock")
    public ResponseEntity<PlanetUnlockResponse> unlockPlanet(
            @AuthMember LoginMember loginMember,
            @PathVariable String planetId) {
        return ResponseEntity.ok(explorationService.unlockPlanet(loginMember.memberId(), planetId));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.exploration.ExplorationControllerTest"`
Expected: PASS (7 테스트)

- [ ] **Step 5: Commit**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/exploration/ SS-Web/src/test/java/com/elipair/spacestudyship/controller/exploration/
git commit -m "탐험 도메인 구현 : feat : ExplorationController 4 엔드포인트 + 테스트 #27"
```

---

## Task 11: Flyway 마이그레이션 (스키마 + 시드 38노드)

**Files:**
- Create: `SS-Web/src/main/resources/db/migration/V0_0_42__add_exploration.sql`
- Modify: `CLAUDE.md` (마이그레이션 이력표)

> **시작 전:** `version.yml`의 `version` 확인. `V0_0_42__*.sql`가 이미 있으면 현재 version.yml 값으로 파일명 변경. 현재 가정: `0.0.42`.

- [ ] **Step 1: 마이그레이션 작성**

```sql
-- exploration_nodes: 행성/지역 마스터 (시드, 읽기 전용)
CREATE TABLE IF NOT EXISTS exploration_nodes (
    id                    VARCHAR(50)  PRIMARY KEY,
    name                  VARCHAR(50)  NOT NULL,
    node_type             VARCHAR(10)  NOT NULL,
    depth                 INTEGER      NOT NULL,
    icon                  VARCHAR(30)  NOT NULL,
    parent_id             VARCHAR(50),
    prerequisite_node_id  VARCHAR(50),
    required_fuel         INTEGER      NOT NULL DEFAULT 0,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    description           VARCHAR(200) NOT NULL DEFAULT '',
    map_x                 DOUBLE PRECISION NOT NULL DEFAULT 0,
    map_y                 DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT fk_expl_node_parent       FOREIGN KEY (parent_id)            REFERENCES exploration_nodes(id),
    CONSTRAINT fk_expl_node_prerequisite FOREIGN KEY (prerequisite_node_id) REFERENCES exploration_nodes(id),
    CONSTRAINT chk_expl_node_type CHECK (node_type IN ('planet','region')),
    CONSTRAINT chk_expl_required_fuel_non_negative CHECK (required_fuel >= 0)
);

-- user_exploration_progress: 유저별 해금 상태 (행 존재 = 해금)
CREATE TABLE IF NOT EXISTS user_exploration_progress (
    id           BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    node_id      VARCHAR(50)  NOT NULL,
    is_unlocked  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_cleared   BOOLEAN      NOT NULL DEFAULT FALSE,
    unlocked_at  TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_user_expl_member FOREIGN KEY (user_id) REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_expl_node   FOREIGN KEY (node_id) REFERENCES exploration_nodes(id),
    CONSTRAINT uq_user_expl UNIQUE (user_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_user_expl_user ON user_exploration_progress (user_id);

-- 시드: 행성 8 (행성 먼저)
INSERT INTO exploration_nodes (id, name, node_type, depth, icon, parent_id, prerequisite_node_id, required_fuel, sort_order, description, map_x, map_y) VALUES
 ('earth',   '지구',   'planet', 2, 'earth',   NULL, NULL,      0,  0, '우리의 출발지, 고향 행성',        0.5,  0.08),
 ('mercury', '수성',   'planet', 2, 'mercury', NULL, 'earth',   3,  1, '태양에 가장 가까운 작은 행성',     0.15, 0.20),
 ('venus',   '금성',   'planet', 2, 'venus',   NULL, 'mercury', 5,  2, '두꺼운 대기로 뒤덮인 뜨거운 행성', 0.75, 0.32),
 ('mars',    '화성',   'planet', 2, 'mars',    NULL, 'venus',   10, 3, '붉은 행성, 탐험의 꿈',            0.25, 0.44),
 ('jupiter', '목성',   'planet', 2, 'jupiter', NULL, 'mars',    20, 4, '태양계 최대의 가스 행성',          0.7,  0.56),
 ('saturn',  '토성',   'planet', 2, 'saturn',  NULL, 'jupiter', 30, 5, '아름다운 고리를 가진 행성',        0.2,  0.68),
 ('uranus',  '천왕성', 'planet', 2, 'uranus',  NULL, 'saturn',  45, 6, '옆으로 누워 자전하는 얼음 행성',   0.8,  0.80),
 ('neptune', '해왕성', 'planet', 2, 'neptune', NULL, 'uranus',  60, 7, '태양계 끝자락의 푸른 행성',        0.35, 0.92)
ON CONFLICT (id) DO NOTHING;

-- 시드: 지역 30 (지구 12 + 그 외 18). region은 prerequisite NULL, map 0/0.
INSERT INTO exploration_nodes (id, name, node_type, depth, icon, parent_id, prerequisite_node_id, required_fuel, sort_order, description, map_x, map_y) VALUES
 ('korea',     '대한민국', 'region', 3, 'KR', 'earth', NULL, 0,  0, '한반도 남쪽, K-컬쳐의 중심', 0, 0),
 ('japan',     '일본',     'region', 3, 'JP', 'earth', NULL, 1,  1, '벚꽃과 기술의 나라',         0, 0),
 ('thailand',  '태국',     'region', 3, 'TH', 'earth', NULL, 1,  2, '미소의 나라, 동남아의 허브', 0, 0),
 ('china',     '중국',     'region', 3, 'CN', 'earth', NULL, 2,  3, '세계 최대 인구 대국',        0, 0),
 ('india',     '인도',     'region', 3, 'IN', 'earth', NULL, 2,  4, 'IT 강국, 다양한 문화의 보고', 0, 0),
 ('uk',        '영국',     'region', 3, 'GB', 'earth', NULL, 2,  5, '해가 지지 않는 나라',        0, 0),
 ('france',    '프랑스',   'region', 3, 'FR', 'earth', NULL, 2,  6, '예술과 낭만의 나라',         0, 0),
 ('canada',    '캐나다',   'region', 3, 'CA', 'earth', NULL, 2,  7, '단풍과 자연의 나라',         0, 0),
 ('usa',       '미국',     'region', 3, 'US', 'earth', NULL, 3,  8, '자유의 나라, 기회의 땅',     0, 0),
 ('brazil',    '브라질',   'region', 3, 'BR', 'earth', NULL, 3,  9, '삼바와 축구의 나라',         0, 0),
 ('australia', '호주',     'region', 3, 'AU', 'earth', NULL, 3, 10, '코알라와 캥거루의 대륙',     0, 0),
 ('egypt',     '이집트',   'region', 3, 'EG', 'earth', NULL, 2, 11, '피라미드와 나일강의 나라',   0, 0),
 ('mercury_caloris',   '칼로리스 분지', 'region', 3, 'mercury', 'mercury', NULL, 1, 0, '수성 최대의 충돌 분지',           0, 0),
 ('mercury_plains',    '북극 평원',     'region', 3, 'mercury', 'mercury', NULL, 2, 1, '얼음이 숨겨진 영구 그림자 지대',   0, 0),
 ('venus_ishtar',      '이슈타르 대지', 'region', 3, 'venus',   'venus',   NULL, 2, 0, '금성 북반구의 거대한 고원 지대',   0, 0),
 ('venus_aphrodite',   '아프로디테 대지','region', 3, 'venus',   'venus',   NULL, 3, 1, '금성 적도를 따라 펼쳐진 최대 대지', 0, 0),
 ('venus_maxwell',     '맥스웰 산',     'region', 3, 'venus',   'venus',   NULL, 3, 2, '금성에서 가장 높은 산맥',         0, 0),
 ('mars_olympus',      '올림푸스 산',   'region', 3, 'mars',    'mars',    NULL, 3, 0, '태양계에서 가장 높은 화산',        0, 0),
 ('mars_valles',       '마리너 계곡',   'region', 3, 'mars',    'mars',    NULL, 4, 1, '태양계 최대의 협곡',             0, 0),
 ('mars_polar',        '극관 지대',     'region', 3, 'mars',    'mars',    NULL, 5, 2, '드라이아이스와 물 얼음의 극지방',  0, 0),
 ('jupiter_red_spot',  '대적점',        'region', 3, 'jupiter', 'jupiter', NULL, 5, 0, '수백 년간 지속되는 거대 폭풍',     0, 0),
 ('jupiter_europa',    '유로파',        'region', 3, 'jupiter', 'jupiter', NULL, 7, 1, '얼음 아래 바다가 있는 위성',       0, 0),
 ('jupiter_io',        '이오',          'region', 3, 'jupiter', 'jupiter', NULL, 8, 2, '화산 활동이 가장 활발한 위성',     0, 0),
 ('saturn_rings',      '토성 고리',     'region', 3, 'saturn',  'saturn',  NULL, 8, 0, '얼음과 먼지로 이루어진 아름다운 고리', 0, 0),
 ('saturn_titan',      '타이탄',        'region', 3, 'saturn',  'saturn',  NULL, 10, 1, '대기를 가진 유일한 위성, 메탄의 호수', 0, 0),
 ('saturn_enceladus',  '엔셀라두스',    'region', 3, 'saturn',  'saturn',  NULL, 12, 2, '간헐천이 분출하는 얼음 위성',     0, 0),
 ('uranus_miranda',    '미란다',        'region', 3, 'uranus',  'uranus',  NULL, 12, 0, '기괴한 지형의 작은 위성',         0, 0),
 ('uranus_atmosphere', '천왕성 대기',   'region', 3, 'uranus',  'uranus',  NULL, 15, 1, '메탄이 만드는 청록빛 대기',       0, 0),
 ('neptune_dark_spot', '대흑점',        'region', 3, 'neptune', 'neptune', NULL, 15, 0, '초속 2000km 폭풍의 소용돌이',     0, 0),
 ('neptune_triton',    '트리톤',        'region', 3, 'neptune', 'neptune', NULL, 20, 1, '역행 궤도를 도는 거대 위성',       0, 0)
ON CONFLICT (id) DO NOTHING;
```

- [ ] **Step 2: 빌드/회귀 확인**

Run: `./gradlew :SS-Study:test :SS-Web:test`
Expected: BUILD SUCCESSFUL (Flyway는 테스트에서 비활성이지만 회귀 확인)

- [ ] **Step 3: CLAUDE.md 이력표 갱신**

"현재 마이그레이션 이력" 표에 행 추가:

```
| 0.0.42 | `V0_0_42__add_exploration.sql` | `exploration_nodes`, `user_exploration_progress` 테이블 + 행성/지역 시드 38노드 (프론트 시드 미러, self-FK, FK CASCADE, UNIQUE) |
```

- [ ] **Step 4: Commit**

```bash
git add SS-Web/src/main/resources/db/migration/V0_0_42__add_exploration.sql CLAUDE.md
git commit -m "탐험 도메인 구현 : chore : exploration 테이블 + 시드 38노드 마이그레이션 #27"
```

---

## Task 12: API 스펙 문서 갱신

**Files:**
- Modify: `docs/api-specs/05_exploration.md`

- [ ] **Step 1: 노드 객체에 prerequisiteId**

"탐험 노드 객체 구조" 필드 표 `parentId` 행 아래에 추가:

```
| `prerequisiteId` | String | O | 선행 행성 ID (행성만, 이 행성을 해금하려면 선행 행성을 클리어해야 함). region은 null |
```

행성 목록 예시 JSON들에 `"prerequisiteId"` 추가 (earth=null, mercury="earth" 등 실제 체인 반영).

- [ ] **Step 2: 행성 해금에 선행 게이트**

"4. 행성 해금" Error 표에 추가:

```
| 400 | `PREREQUISITE_NOT_CLEARED` | 선행 행성이 아직 클리어되지 않음 |
```

서버 처리 로직 "2. 이미 해금된 행성인지 확인" 다음에 추가:

```
  2-1. prerequisiteId가 있으면 선행 행성이 클리어(모든 하위 지역 해금)되었는지 확인 → 아니면 PREREQUISITE_NOT_CLEARED
```

"해금 규칙" 개요에 추가:

```
- **행성 진행 게이트**: 행성은 선행 행성(prerequisiteId)을 클리어해야 해금. 지구는 선행 없음 (체인: 지구→수성→금성→화성→목성→토성→천왕성→해왕성).
```

- [ ] **Step 3: DB 테이블 + 시드/연료/ID 규칙 정정**

- `exploration_nodes` 컬럼 표에 `prerequisite_node_id` (VARCHAR(50), FK→self) 추가.
- 개요 트리/예시 연료 수치를 본 시드값(행성 0/3/5/10/20/30/45/60, 지역 0~20)으로 정정.
- region ID는 이름 기반(`korea`,`mars_olympus`), icon은 지구지역=국가코드/그 외=행성이름임을 명시.
- 행성 로스터를 8행성(달 없음, 천왕성 포함)으로 정정.

- [ ] **Step 4: INSUFFICIENT_FUEL 응답 보강 명시**

지역/행성 해금 에러 섹션에 INSUFFICIENT_FUEL 응답 본문이 `requiredFuel`/`currentFuel`을 포함함을 예시와 함께 명시:

```json
{ "code": "INSUFFICIENT_FUEL", "message": "연료가 부족합니다.", "requiredFuel": 10, "currentFuel": 4 }
```

- [ ] **Step 5: Commit**

```bash
git add docs/api-specs/05_exploration.md
git commit -m "탐험 도메인 구현 : docs : 05_exploration 스펙 frontend 계약 정합 갱신 #27"
```

---

## 최종 검증

- [ ] **전체 테스트**

Run: `./gradlew :SS-Common:test :SS-Study:test :SS-Web:test`
Expected: BUILD SUCCESSFUL — 신규 통과, 회귀 없음

- [ ] **시드 정합 spot-check**

`V0_0_42__add_exploration.sql`의 행성 8 + 지역 30 = 38행, 프론트 시드(`exploration_seed_data.dart`)의 id/icon/required_fuel/sort_order와 일치하는지 대조.

---

## Self-Review (작성자 기록)

- **Spec coverage:** Task0 폐기 / Task1 에러인프라(ErrorCode·ErrorResponse·예외·핸들러) / Task2 NodeType / Task3-4 엔티티 / Task5 repo / Task6 DTO / Task7 조회 / Task8 지역해금+pre-check / Task9 행성해금+게이트 / Task10 컨트롤러 / Task11 마이그레이션 38노드 / Task12 문서. spec 전 항목 매핑.
- **Type 일관성:** `fuelService.getFuel(userId).currentFuel()`(FuelResponse), `consume(...)`→`FuelTransactionResponse.amount()/balanceAfter()`, `InsufficientFuelException(requiredFuel,currentFuel)`, `ErrorResponse.ofInsufficientFuel(msg,req,cur)`, `UserExploration.unlock(userId,nodeId,cleared)`, DTO `of(...)` 시그니처가 service 호출과 일치.
- **시드:** 8행성+30지역=38, 프론트 시드 1:1 (id/icon/fuel/sortOrder/description/mapXY).
- **Placeholder:** 없음.
