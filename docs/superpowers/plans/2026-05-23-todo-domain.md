# 할 일 + 카테고리 도메인 구현 계획 (이슈 #24)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** API 스펙 02_todo.md의 Todo + Category CRUD 8개 엔드포인트를 SS-Study 모듈에 구현하고 Swagger 문서·일관된 ErrorResponse·Flyway 마이그레이션까지 포함해 PR 직전 상태로 만든다.

**Architecture:** SS-Study 모듈의 `todo/` 패키지에 Entity/Repo/Service, SS-Web에 Controller 배치. Tier 1 (Optimistic Updates) — 클라이언트가 UUID 생성, 서버는 검증·영속화·소유권 확인. 배열 데이터(scheduledDates 등)는 PostgreSQL JSONB + `@JdbcTypeCode(SqlTypes.JSON)`. 다른 사용자 리소스 접근은 404로 통일 (403 미사용). 카테고리 삭제 시 연관 Todo의 categoryIds는 JPA dirty checking으로 정리.

**Tech Stack:** Java 21, Spring Boot 4.0.2-SNAPSHOT, JPA + Hibernate 6, PostgreSQL (운영) / Testcontainers PostgreSQL (Repository 테스트) / Mockito (Service 단위) / MockMvc (Controller), Flyway, Lombok, Jakarta Validation, springdoc-openapi.

**Spec:** [docs/superpowers/specs/2026-05-23-todo-domain-design.md](../specs/2026-05-23-todo-domain-design.md)

**커밋 메시지 형식 (이 프로젝트):** `{이슈제목} : {type} : {설명} #{이슈번호}` — 예: `할일 및 카테고리 도메인 구현 : feat : Todo Entity 추가 #24`

---

## Task 진행 순서 개요

| # | Task | Phase |
|---|------|-------|
| 1 | SS-Common ErrorCode 4개 추가 | Foundation |
| 2 | version.yml bump + V0_0_34 마이그레이션 SQL | Foundation |
| 3 | SS-Study build.gradle Testcontainers + 테스트 config | Foundation |
| 4 | StudyTestApplication 셋업 | Foundation |
| 5 | TodoCategory Entity | Category 도메인 |
| 6 | TodoCategoryRepository + Repository 테스트 | Category 도메인 |
| 7 | Category DTO 3개 (Record) | Category 도메인 |
| 8 | Todo Entity | Todo 도메인 |
| 9 | TodoRepository + Repository 테스트 (JSONB) | Todo 도메인 |
| 10 | Todo DTO 3개 (Record) | Todo 도메인 |
| 11 | TodoService.findAll (필터 조합) | Service |
| 12 | TodoService.create + categoryIds 검증 | Service |
| 13 | TodoService.update (PATCH) | Service |
| 14 | TodoService.delete | Service |
| 15 | TodoCategoryService.findAll/create/update | Service |
| 16 | TodoCategoryService.delete (cascade Todo) | Service |
| 17 | TodoController + Swagger + MockMvc | Controller |
| 18 | TodoCategoryController + Swagger + MockMvc | Controller |
| 19 | CLAUDE.md 이력 업데이트 + 최종 검증 | Wrap-up |

---

## 파일 구조

```
SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/
└── ErrorCode.java                                  [MODIFY: 4개 추가]

SS-Study/build.gradle                               [MODIFY: testcontainers]
SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/
├── dto/
│   ├── TodoCreateRequest.java                      [CREATE]
│   ├── TodoUpdateRequest.java                      [CREATE]
│   ├── TodoResponse.java                           [CREATE]
│   ├── CategoryCreateRequest.java                  [CREATE]
│   ├── CategoryUpdateRequest.java                  [CREATE]
│   └── CategoryResponse.java                       [CREATE]
├── entity/
│   ├── Todo.java                                   [CREATE]
│   └── TodoCategory.java                           [CREATE]
├── repository/
│   ├── TodoRepository.java                         [CREATE]
│   └── TodoCategoryRepository.java                 [CREATE]
└── service/
    ├── TodoService.java                            [CREATE]
    └── TodoCategoryService.java                    [CREATE]

SS-Study/src/test/java/com/elipair/spacestudyship/study/
├── StudyTestApplication.java                       [CREATE]
└── todo/
    ├── entity/
    │   ├── TodoTest.java                           [CREATE]
    │   └── TodoCategoryTest.java                   [CREATE]
    ├── repository/
    │   ├── TodoRepositoryTest.java                 [CREATE]
    │   └── TodoCategoryRepositoryTest.java         [CREATE]
    └── service/
        ├── TodoServiceTest.java                    [CREATE]
        └── TodoCategoryServiceTest.java            [CREATE]
SS-Study/src/test/resources/application.yml         [CREATE]

SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/
├── TodoController.java                             [CREATE]
└── TodoCategoryController.java                     [CREATE]

SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/
├── TodoControllerTest.java                         [CREATE]
└── TodoCategoryControllerTest.java                 [CREATE]

SS-Web/src/main/resources/db/migration/
└── V0_0_34__add_todos_and_categories.sql           [CREATE]

version.yml                                          [MODIFY: 0.0.33 → 0.0.34]
CLAUDE.md                                            [MODIFY: 이력 표]
```

---

## Task 1: SS-Common ErrorCode 4개 추가

**Files:**
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java`

- [ ] **Step 1: 기존 ErrorCode 파일을 열고 새 항목 추가 위치 확인**

`ErrorCode.java`의 마지막 enum 항목 `INTERNAL_SERVER_ERROR(...)` 바로 위에 추가. 다음 4개를 `// Member` 그룹 아래 새 그룹으로 삽입.

- [ ] **Step 2: ErrorCode 4개 추가**

```java
// Todo
TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 할 일을 찾을 수 없습니다."),
TODO_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 할 일이 이미 존재합니다."),

// Todo Category
CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 카테고리를 찾을 수 없습니다."),
CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 카테고리가 이미 존재합니다."),
```

`// Common` 주석 그룹 바로 위에 위치하도록 배치.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :SS-Common:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : ErrorCode 4개 추가 (Todo, Category NotFound/Conflict) #24"
```

---

## Task 2: version.yml bump + V0_0_34 마이그레이션 SQL

**Files:**
- Modify: `version.yml`
- Create: `SS-Web/src/main/resources/db/migration/V0_0_34__add_todos_and_categories.sql`

- [ ] **Step 1: version.yml의 version 값 변경**

`version.yml`에서 `version: "0.0.33"` → `version: "0.0.34"`, `version_code: 33` → `version_code: 34`로 변경. `last_updated`, `last_updated_by`는 자동화 워크플로우가 처리하므로 손대지 않음.

- [ ] **Step 2: build.gradle 루트의 version 동기화**

`build.gradle` (루트)의 `version = '0.0.33'` → `version = '0.0.34'` 변경.

- [ ] **Step 3: V0_0_34 마이그레이션 SQL 작성**

Create `SS-Web/src/main/resources/db/migration/V0_0_34__add_todos_and_categories.sql`:

```sql
-- todo_categories: 카테고리 (할 일보다 먼저 생성)
CREATE TABLE IF NOT EXISTS todo_categories (
    id          VARCHAR(36)      PRIMARY KEY,
    user_id     BIGINT           NOT NULL,
    name        VARCHAR(20)      NOT NULL,
    icon_id     VARCHAR(50),
    position_x  DOUBLE PRECISION,
    position_y  DOUBLE PRECISION,
    created_at  TIMESTAMP        NOT NULL,
    updated_at  TIMESTAMP        NOT NULL,
    CONSTRAINT fk_todo_categories_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_todo_categories_user ON todo_categories(user_id);

-- todos: 할 일
CREATE TABLE IF NOT EXISTS todos (
    id                 VARCHAR(36)  PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    title              VARCHAR(100) NOT NULL,
    scheduled_dates    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    completed_dates    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    category_ids       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    estimated_minutes  INTEGER,
    actual_minutes     INTEGER,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_todos_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_todos_user ON todos(user_id);
```

- [ ] **Step 4: 마이그레이션 파일 문법 검증 (Gradle 컴파일)**

Run: `./gradlew :SS-Web:compileJava`
Expected: BUILD SUCCESSFUL (SQL 파일은 검증되지 않으나 다른 파일 영향 없음 확인)

- [ ] **Step 5: 커밋**

```bash
git add version.yml build.gradle SS-Web/src/main/resources/db/migration/V0_0_34__add_todos_and_categories.sql
git commit -m "할일 및 카테고리 도메인 구현 : chore : 버전 0.0.34 bump 및 V0_0_34 마이그레이션 추가 #24"
```

---

## Task 3: SS-Study build.gradle + 테스트 config (Testcontainers)

**Files:**
- Modify: `SS-Study/build.gradle`
- Create: `SS-Study/src/test/resources/application.yml`

- [ ] **Step 1: SS-Study build.gradle에 testcontainers 의존성 추가**

기존 내용 위에 다음과 같이 변경:

```gradle
bootJar {
    enabled = false
}

jar {
    enabled = true
    archiveClassifier = ''
}

dependencies {
    api project(':SS-Common')
    api project(':SS-Member')

    // Test - Testcontainers PostgreSQL (JSONB 쿼리 검증용)
    testImplementation 'org.testcontainers:testcontainers:1.20.4'
    testImplementation 'org.testcontainers:postgresql:1.20.4'
    testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
    testRuntimeOnly 'org.postgresql:postgresql'
}
```

- [ ] **Step 2: SS-Study 테스트용 application.yml 작성**

Create `SS-Study/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    # JdbcDatabaseDelegate가 Testcontainers JDBC URL을 동적으로 처리
    url: jdbc:tc:postgresql:16:///studytest
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  flyway:
    enabled: false
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew :SS-Study:dependencies --configuration testRuntimeClasspath | head -50`
Expected: testcontainers, postgresql 의존성 표시. Docker가 로컬에 실행 중이어야 실제 테스트 동작 — 의존성 해석만 확인.

- [ ] **Step 4: 커밋**

```bash
git add SS-Study/build.gradle SS-Study/src/test/resources/application.yml
git commit -m "할일 및 카테고리 도메인 구현 : chore : SS-Study Testcontainers PostgreSQL 의존성 추가 #24"
```

---

## Task 4: StudyTestApplication 셋업

**Files:**
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java`

- [ ] **Step 1: TestAuthApplication 패턴을 그대로 적용**

Create file:

```java
package com.elipair.spacestudyship.study;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@AutoConfigurationPackage(basePackages = "com.elipair.spacestudyship")
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.elipair.spacestudyship.study.todo.repository")
public class StudyTestApplication {
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :SS-Study:compileTestJava`
Expected: BUILD SUCCESSFUL.

> 스모크 테스트(`@SpringBootTest` 컨텍스트 로딩 검증)는 Entity가 아직 없어 JPA 메타데이터 초기화가 불완전하므로 Task 6에서 실제 Repository 테스트와 함께 처음 검증된다. Task 4는 클래스 컴파일만 확인.

- [ ] **Step 3: 커밋**

```bash
git add SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java
git commit -m "할일 및 카테고리 도메인 구현 : test : SS-Study 테스트 셋업 (StudyTestApplication) #24"
```

---

## Task 5: TodoCategory Entity

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/entity/TodoCategoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 — 정적 팩토리 + updateXxx**

Create test file:

```java
package com.elipair.spacestudyship.study.todo.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TodoCategoryTest {

    @Test
    @DisplayName("create: 정적 팩토리로 카테고리 생성")
    void create() {
        TodoCategory category = TodoCategory.create(
                "cat-1", 1L, "수학", "math_icon", 0.3, 0.5);

        assertThat(category.getId()).isEqualTo("cat-1");
        assertThat(category.getUserId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("수학");
        assertThat(category.getIconId()).isEqualTo("math_icon");
        assertThat(category.getPositionX()).isEqualTo(0.3);
        assertThat(category.getPositionY()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("updateName: 이름 변경")
    void updateName() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", null, null, null);
        category.updateName("심화수학");
        assertThat(category.getName()).isEqualTo("심화수학");
    }

    @Test
    @DisplayName("updateIconId: 아이콘 변경")
    void updateIconId() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", "math_icon", null, null);
        category.updateIconId("new_icon");
        assertThat(category.getIconId()).isEqualTo("new_icon");
    }

    @Test
    @DisplayName("updatePositionX/Y: 위치 변경")
    void updatePosition() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", null, 0.3, 0.5);
        category.updatePositionX(0.7);
        category.updatePositionY(0.2);
        assertThat(category.getPositionX()).isEqualTo(0.7);
        assertThat(category.getPositionY()).isEqualTo(0.2);
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryTest"`
Expected: FAIL with "cannot find symbol class TodoCategory"

- [ ] **Step 3: TodoCategory Entity 구현**

Create `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java`:

```java
package com.elipair.spacestudyship.study.todo.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
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
@Table(name = "todo_categories")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoCategory extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "icon_id", length = 50)
    private String iconId;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    public static TodoCategory create(String id, Long userId, String name,
                                      String iconId, Double positionX, Double positionY) {
        return TodoCategory.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .iconId(iconId)
                .positionX(positionX)
                .positionY(positionY)
                .build();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateIconId(String iconId) {
        this.iconId = iconId;
    }

    public void updatePositionX(Double positionX) {
        this.positionX = positionX;
    }

    public void updatePositionY(Double positionY) {
        this.positionY = positionY;
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/entity/TodoCategoryTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoCategory Entity 추가 #24"
```

---

## Task 6: TodoCategoryRepository + Repository 테스트

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepository.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java`

- [ ] **Step 1: 실패하는 Repository 테스트 작성**

Create test:

```java
package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class TodoCategoryRepositoryTest {

    @Autowired
    TodoCategoryRepository categoryRepository;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtAsc: 사용자 카테고리를 생성일 오름차순으로 반환")
    void findByUserIdOrderByCreatedAtAsc() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        categoryRepository.save(TodoCategory.create("c2", 1L, "영어", null, null, null));
        categoryRepository.save(TodoCategory.create("c3", 2L, "다른유저", null, null, null));

        List<TodoCategory> result = categoryRepository.findByUserIdOrderByCreatedAtAsc(1L);

        assertThat(result).extracting("id").containsExactly("c1", "c2");
    }

    @Test
    @DisplayName("existsByIdAndUserId: 본인 카테고리는 true")
    void existsByIdAndUserId_true() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        assertThat(categoryRepository.existsByIdAndUserId("c1", 1L)).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndUserId: 다른 사용자 카테고리는 false")
    void existsByIdAndUserId_otherUser() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        assertThat(categoryRepository.existsByIdAndUserId("c1", 2L)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndUserId: 본인 카테고리만 조회")
    void findByIdAndUserId() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));

        Optional<TodoCategory> mine = categoryRepository.findByIdAndUserId("c1", 1L);
        Optional<TodoCategory> other = categoryRepository.findByIdAndUserId("c1", 99L);

        assertThat(mine).isPresent();
        assertThat(other).isEmpty();
    }

    @Test
    @DisplayName("countByIdInAndUserId: 본인 소유 카테고리 ID 개수")
    void countByIdInAndUserId() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        categoryRepository.save(TodoCategory.create("c2", 1L, "영어", null, null, null));
        categoryRepository.save(TodoCategory.create("c3", 2L, "다른유저", null, null, null));

        long count = categoryRepository.countByIdInAndUserId(List.of("c1", "c2", "c3"), 1L);

        assertThat(count).isEqualTo(2L);
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryRepositoryTest"`
Expected: FAIL with "cannot find symbol class TodoCategoryRepository"

- [ ] **Step 3: TodoCategoryRepository 구현**

Create `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepository.java`:

```java
package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TodoCategoryRepository extends JpaRepository<TodoCategory, String> {

    List<TodoCategory> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByIdAndUserId(String id, Long userId);

    Optional<TodoCategory> findByIdAndUserId(String id, Long userId);

    long countByIdInAndUserId(Collection<String> ids, Long userId);
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryRepositoryTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepository.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoCategoryRepository 추가 #24"
```

---

## Task 7: Category DTO 3개 (Record)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryCreateRequest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryUpdateRequest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryResponse.java`

- [ ] **Step 1: CategoryCreateRequest 작성**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 생성 요청")
public record CategoryCreateRequest(

        @Schema(description = "클라이언트 UUID (없으면 서버 생성)", nullable = true,
                example = "cat-uuid-3")
        String id,

        @Schema(description = "카테고리 이름 (1~20자)", example = "수학")
        @NotBlank
        @Size(max = 20)
        String name,

        @Schema(description = "아이콘 식별자", nullable = true, example = "math_icon")
        String iconId,

        @Schema(description = "맵 가로 위치 (0.0~1.0)", nullable = true, example = "0.3")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionX,

        @Schema(description = "맵 세로 위치 (0.0~1.0)", nullable = true, example = "0.5")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionY
) {
}
```

- [ ] **Step 2: CategoryUpdateRequest 작성**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record CategoryUpdateRequest(

        @Schema(description = "카테고리 이름 (1~20자)", nullable = true)
        @Size(min = 1, max = 20)
        String name,

        @Schema(description = "아이콘 식별자", nullable = true)
        String iconId,

        @Schema(description = "맵 가로 위치 (0.0~1.0)", nullable = true)
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionX,

        @Schema(description = "맵 세로 위치 (0.0~1.0)", nullable = true)
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionY
) {
}
```

- [ ] **Step 3: CategoryResponse 작성**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "cat-uuid-1") String id,
        @Schema(description = "이름", example = "수학") String name,
        @Schema(description = "아이콘 식별자", nullable = true) String iconId,
        @Schema(description = "맵 가로 위치", nullable = true) Double positionX,
        @Schema(description = "맵 세로 위치", nullable = true) Double positionY,
        @Schema(description = "생성 시각 (ISO 8601 UTC)") String createdAt,
        @Schema(description = "마지막 수정 시각 (ISO 8601 UTC)", nullable = true) String updatedAt
) {
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ISO_INSTANT;

    public static CategoryResponse from(TodoCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIconId(),
                category.getPositionX(),
                category.getPositionY(),
                formatUtc(category.getCreatedAt()),
                formatUtc(category.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :SS-Study:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryCreateRequest.java SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryUpdateRequest.java SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/CategoryResponse.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : Category DTO 3개 추가 (Record) #24"
```

---

## Task 8: Todo Entity

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/entity/TodoTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

Create:

```java
package com.elipair.spacestudyship.study.todo.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoTest {

    @Test
    @DisplayName("create: 정적 팩토리로 Todo 생성 — null 배열은 빈 배열로 정규화")
    void create_nullArraysNormalizedToEmpty() {
        Todo todo = Todo.create("t1", 1L, "수학 문제", null, null, 60);

        assertThat(todo.getId()).isEqualTo("t1");
        assertThat(todo.getUserId()).isEqualTo(1L);
        assertThat(todo.getTitle()).isEqualTo("수학 문제");
        assertThat(todo.getScheduledDates()).isEmpty();
        assertThat(todo.getCompletedDates()).isEmpty();
        assertThat(todo.getCategoryIds()).isEmpty();
        assertThat(todo.getEstimatedMinutes()).isEqualTo(60);
        assertThat(todo.getActualMinutes()).isNull();
    }

    @Test
    @DisplayName("create: 값이 있으면 그대로 사용")
    void create_withValues() {
        Todo todo = Todo.create(
                "t1", 1L, "수학",
                List.of("2026-04-16"),
                List.of("cat-1"),
                90);

        assertThat(todo.getScheduledDates()).containsExactly("2026-04-16");
        assertThat(todo.getCategoryIds()).containsExactly("cat-1");
    }

    @Test
    @DisplayName("updateTitle / updateScheduledDates / updateCompletedDates / updateCategoryIds / updateEstimatedMinutes / updateActualMinutes")
    void updaters() {
        Todo todo = Todo.create("t1", 1L, "원본", null, null, null);

        todo.updateTitle("새 제목");
        todo.updateScheduledDates(List.of("2026-05-01"));
        todo.updateCompletedDates(List.of("2026-05-01"));
        todo.updateCategoryIds(List.of("c1", "c2"));
        todo.updateEstimatedMinutes(120);
        todo.updateActualMinutes(45);

        assertThat(todo.getTitle()).isEqualTo("새 제목");
        assertThat(todo.getScheduledDates()).containsExactly("2026-05-01");
        assertThat(todo.getCompletedDates()).containsExactly("2026-05-01");
        assertThat(todo.getCategoryIds()).containsExactly("c1", "c2");
        assertThat(todo.getEstimatedMinutes()).isEqualTo(120);
        assertThat(todo.getActualMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("removeCategoryId: 해당 ID만 제거 (immutable copy)")
    void removeCategoryId() {
        Todo todo = Todo.create("t1", 1L, "수학", null, List.of("c1", "c2", "c3"), null);

        todo.removeCategoryId("c2");

        assertThat(todo.getCategoryIds()).containsExactly("c1", "c3");
    }

    @Test
    @DisplayName("removeCategoryId: 존재하지 않는 ID면 무변화")
    void removeCategoryId_notExist() {
        Todo todo = Todo.create("t1", 1L, "수학", null, List.of("c1"), null);

        todo.removeCategoryId("c-missing");

        assertThat(todo.getCategoryIds()).containsExactly("c1");
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoTest"`
Expected: FAIL with "cannot find symbol class Todo"

- [ ] **Step 3: Todo Entity 구현**

Create `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java`:

```java
package com.elipair.spacestudyship.study.todo.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "todos")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scheduled_dates", nullable = false, columnDefinition = "jsonb")
    private List<String> scheduledDates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_dates", nullable = false, columnDefinition = "jsonb")
    private List<String> completedDates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> categoryIds;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    public static Todo create(String id, Long userId, String title,
                              List<String> scheduledDates,
                              List<String> categoryIds,
                              Integer estimatedMinutes) {
        return Todo.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .scheduledDates(scheduledDates == null ? new ArrayList<>() : new ArrayList<>(scheduledDates))
                .completedDates(new ArrayList<>())
                .categoryIds(categoryIds == null ? new ArrayList<>() : new ArrayList<>(categoryIds))
                .estimatedMinutes(estimatedMinutes)
                .build();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateScheduledDates(List<String> dates) {
        this.scheduledDates = new ArrayList<>(dates);
    }

    public void updateCompletedDates(List<String> dates) {
        this.completedDates = new ArrayList<>(dates);
    }

    public void updateCategoryIds(List<String> ids) {
        this.categoryIds = new ArrayList<>(ids);
    }

    public void updateEstimatedMinutes(Integer minutes) {
        this.estimatedMinutes = minutes;
    }

    public void updateActualMinutes(Integer minutes) {
        this.actualMinutes = minutes;
    }

    public void removeCategoryId(String categoryId) {
        this.categoryIds = this.categoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/entity/TodoTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : Todo Entity 추가 (JSONB 매핑) #24"
```

---

## Task 9: TodoRepository + Repository 테스트 (JSONB `@>` 쿼리)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java`

- [ ] **Step 1: 실패하는 Repository 테스트 작성 — 5개 케이스**

Create test:

```java
package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.todo.entity.Todo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class TodoRepositoryTest {

    @Autowired
    TodoRepository todoRepository;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc: 본인 Todo만, 최신순 반환")
    void findByUserId_ordered() {
        todoRepository.save(Todo.create("t1", 1L, "첫번째", null, null, null));
        todoRepository.save(Todo.create("t2", 1L, "두번째", null, null, null));
        todoRepository.save(Todo.create("t3", 2L, "다른유저", null, null, null));

        List<Todo> result = todoRepository.findByUserIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("userId").containsOnly(1L);
    }

    @Test
    @DisplayName("findByUserIdAndScheduledDate: JSONB @> 연산자로 날짜 포함 Todo 필터")
    void findByUserIdAndScheduledDate() {
        todoRepository.save(Todo.create("t1", 1L, "월요일", List.of("2026-04-16"), null, null));
        todoRepository.save(Todo.create("t2", 1L, "양일", List.of("2026-04-16", "2026-04-17"), null, null));
        todoRepository.save(Todo.create("t3", 1L, "다른날", List.of("2026-04-18"), null, null));

        List<Todo> result = todoRepository
                .findByUserIdAndScheduledDate(1L, "\"2026-04-16\"");

        assertThat(result).extracting("id").containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("findByUserIdAndCategoryId: JSONB @> 연산자로 카테고리 포함 Todo 필터")
    void findByUserIdAndCategoryId() {
        todoRepository.save(Todo.create("t1", 1L, "수학", null, List.of("c-math"), null));
        todoRepository.save(Todo.create("t2", 1L, "복합", null, List.of("c-math", "c-eng"), null));
        todoRepository.save(Todo.create("t3", 1L, "영어만", null, List.of("c-eng"), null));

        List<Todo> result = todoRepository
                .findByUserIdAndCategoryId(1L, "\"c-math\"");

        assertThat(result).extracting("id").containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("existsByIdAndUserId: 본인 소유 여부")
    void existsByIdAndUserId() {
        todoRepository.save(Todo.create("t1", 1L, "X", null, null, null));
        assertThat(todoRepository.existsByIdAndUserId("t1", 1L)).isTrue();
        assertThat(todoRepository.existsByIdAndUserId("t1", 99L)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndUserId: 본인 소유만 조회")
    void findByIdAndUserId() {
        todoRepository.save(Todo.create("t1", 1L, "X", null, null, null));

        Optional<Todo> mine = todoRepository.findByIdAndUserId("t1", 1L);
        Optional<Todo> other = todoRepository.findByIdAndUserId("t1", 99L);

        assertThat(mine).isPresent();
        assertThat(other).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoRepositoryTest"`
Expected: FAIL with "cannot find symbol class TodoRepository"

- [ ] **Step 3: TodoRepository 구현**

Create `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java`:

```java
package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, String> {

    List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND scheduled_dates @> CAST(:dateJson AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndScheduledDate(@Param("userId") Long userId,
                                            @Param("dateJson") String dateJsonLiteral);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND category_ids @> CAST(:categoryJson AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndCategoryId(@Param("userId") Long userId,
                                         @Param("categoryJson") String categoryJsonLiteral);

    boolean existsByIdAndUserId(String id, Long userId);

    Optional<Todo> findByIdAndUserId(String id, Long userId);
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoRepositoryTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoRepository 추가 (JSONB @> 쿼리) #24"
```

---

## Task 10: Todo DTO 3개 (Record)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoCreateRequest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoUpdateRequest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoResponse.java`

- [ ] **Step 1: TodoCreateRequest**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "할 일 생성 요청")
public record TodoCreateRequest(

        @Schema(description = "클라이언트 UUID v4 (없으면 서버 생성)", nullable = true,
                example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "제목 (1~100자)", example = "수학 문제 풀기")
        @NotBlank
        @Size(max = 100)
        String title,

        @Schema(description = "카테고리 ID 목록 (기본 [])", example = "[\"cat-uuid-1\"]")
        List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분, 1 이상)", nullable = true, example = "60")
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", example = "[\"2026-04-16\"]")
        List<String> scheduledDates
) {
}
```

- [ ] **Step 2: TodoUpdateRequest**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "할 일 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record TodoUpdateRequest(

        @Schema(description = "제목 (1~100자)", nullable = true)
        @Size(min = 1, max = 100)
        String title,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<String> scheduledDates,

        @Schema(description = "완료 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<String> completedDates,

        @Schema(description = "카테고리 ID 목록", nullable = true)
        List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true)
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true)
        @Min(0)
        Integer actualMinutes
) {
}
```

- [ ] **Step 3: TodoResponse**

Create:

```java
package com.elipair.spacestudyship.study.todo.dto;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "할 일 응답")
public record TodoResponse(
        @Schema(description = "Todo ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "제목") String title,

        @Schema(description = "예정 날짜 목록") List<String> scheduledDates,

        @Schema(description = "완료 날짜 목록") List<String> completedDates,

        @Schema(description = "카테고리 ID 목록") List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true) Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true) Integer actualMinutes,

        @Schema(description = "생성 시각 (ISO 8601 UTC)") String createdAt,

        @Schema(description = "마지막 수정 시각 (ISO 8601 UTC)") String updatedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getScheduledDates(),
                todo.getCompletedDates(),
                todo.getCategoryIds(),
                todo.getEstimatedMinutes(),
                todo.getActualMinutes(),
                formatUtc(todo.getCreatedAt()),
                formatUtc(todo.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :SS-Study:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoCreateRequest.java SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoUpdateRequest.java SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/dto/TodoResponse.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : Todo DTO 3개 추가 (Record) #24"
```

---

## Task 11: TodoService.findAll (필터 조합)

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성 — findAll 4개 케이스**

Create:

```java
package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock TodoRepository todoRepository;
    @Mock TodoCategoryRepository categoryRepository;
    @InjectMocks TodoService todoService;

    @Test
    @DisplayName("findAll: 필터 없음 → findByUserIdOrderByCreatedAtDesc 호출")
    void findAll_noFilters() {
        when(todoRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", null, null, null)));

        // null literal 직접 전달 (eq(null) 매처는 Mockito mixed-matcher 제약으로 사용 불가)
        var result = todoService.findAll(1L, null, null);

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("findAll: date 필터만")
    void findAll_dateOnly() {
        when(todoRepository.findByUserIdAndScheduledDate(1L, "\"2026-04-16\""))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", List.of("2026-04-16"), null, null)));

        var result = todoService.findAll(1L, "2026-04-16", null);

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdAndScheduledDate(1L, "\"2026-04-16\"");
    }

    @Test
    @DisplayName("findAll: categoryId 필터만")
    void findAll_categoryOnly() {
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", null, List.of("c1"), null)));

        var result = todoService.findAll(1L, null, "c1");

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdAndCategoryId(1L, "\"c1\"");
    }

    @Test
    @DisplayName("findAll: date + categoryId — 두 쿼리 결과의 교집합")
    void findAll_dateAndCategory() {
        Todo a = Todo.create("a", 1L, "AB", List.of("2026-04-16"), List.of("c1"), null);
        Todo b = Todo.create("b", 1L, "B만", List.of("2026-04-16"), List.of("c2"), null);
        when(todoRepository.findByUserIdAndScheduledDate(1L, "\"2026-04-16\""))
                .thenReturn(List.of(a, b));
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(a));

        var result = todoService.findAll(1L, "2026-04-16", "c1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a");
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: FAIL with "cannot find symbol class TodoService"

- [ ] **Step 3: TodoService 스켈레톤 + findAll 구현**

Create:

```java
package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.entity.Todo;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoCategoryRepository categoryRepository;

    public List<TodoResponse> findAll(Long userId, String date, String categoryId) {
        List<Todo> todos;
        if (date != null && categoryId != null) {
            Set<String> byDateIds = todoRepository
                    .findByUserIdAndScheduledDate(userId, jsonLiteral(date))
                    .stream()
                    .map(Todo::getId)
                    .collect(Collectors.toSet());
            todos = todoRepository
                    .findByUserIdAndCategoryId(userId, jsonLiteral(categoryId))
                    .stream()
                    .filter(t -> byDateIds.contains(t.getId()))
                    .toList();
        } else if (date != null) {
            todos = todoRepository.findByUserIdAndScheduledDate(userId, jsonLiteral(date));
        } else if (categoryId != null) {
            todos = todoRepository.findByUserIdAndCategoryId(userId, jsonLiteral(categoryId));
        } else {
            todos = todoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return todos.stream().map(TodoResponse::from).toList();
    }

    private static String jsonLiteral(String value) {
        return "\"" + value + "\"";
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoService.findAll 추가 (필터 조합) #24"
```

---

## Task 12: TodoService.create + categoryIds 검증

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가 — create 3개 케이스**

Append to `TodoServiceTest.java` 클래스 내부 (다른 import 필요: ArgumentCaptor, anyString, anyLong, anyCollection, UUID 등):

```java
    @Test
    @DisplayName("create: id 미지정 → 서버가 UUID 생성, 카테고리 검증 통과")
    void create_serverGeneratedId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                null, "수학", java.util.List.of(), 60, java.util.List.of("2026-04-16"));
        when(todoRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(todoRepository.save(org.mockito.ArgumentMatchers.any(Todo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = todoService.create(1L, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.title()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 동일 ID 존재 → TODO_ALREADY_EXISTS")
    void create_duplicateId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t1", "수학", java.util.List.of(), null, java.util.List.of());
        when(todoRepository.existsById("t1")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.create(1L, request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("create: categoryIds에 존재하지 않는 ID → CATEGORY_NOT_FOUND")
    void create_invalidCategoryId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t1", "수학", java.util.List.of("missing-cat"), null, java.util.List.of());
        when(todoRepository.existsById("t1")).thenReturn(false);
        when(categoryRepository.countByIdInAndUserId(java.util.List.of("missing-cat"), 1L))
                .thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.create(1L, request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.CATEGORY_NOT_FOUND);
    }
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: FAIL with "create not defined"

- [ ] **Step 3: TodoService.create + validateCategoryIds 구현**

`TodoService.java`에 다음 메소드 추가 (클래스 마지막 `}` 직전):

```java
    @Transactional
    public TodoResponse create(Long userId, com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest request) {
        String id = request.id() != null ? request.id() : java.util.UUID.randomUUID().toString();
        if (todoRepository.existsById(id)) {
            throw new com.elipair.spacestudyship.common.exception.CustomException(
                    com.elipair.spacestudyship.common.exception.ErrorCode.TODO_ALREADY_EXISTS);
        }
        validateCategoryIds(userId, request.categoryIds());

        Todo todo = Todo.create(
                id, userId, request.title(),
                request.scheduledDates(),
                request.categoryIds(),
                request.estimatedMinutes());
        Todo saved = todoRepository.save(todo);
        log.info("[Todo] 생성 | userId={}, todoId={}", userId, saved.getId());
        return TodoResponse.from(saved);
    }

    private void validateCategoryIds(Long userId, List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return;
        List<String> distinct = categoryIds.stream().distinct().toList();
        long found = categoryRepository.countByIdInAndUserId(distinct, userId);
        if (found != distinct.size()) {
            throw new com.elipair.spacestudyship.common.exception.CustomException(
                    com.elipair.spacestudyship.common.exception.ErrorCode.CATEGORY_NOT_FOUND);
        }
    }
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoService.create + 카테고리 실존 검증 #24"
```

---

## Task 13: TodoService.update (PATCH partial)

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가 — update 4개 케이스**

Append to `TodoServiceTest.java`:

```java
    @Test
    @DisplayName("update: title만 변경, 나머지 null → 기존 유지")
    void update_titleOnly() {
        Todo existing = Todo.create("t1", 1L, "원본", java.util.List.of("2026-04-16"),
                java.util.List.of("c1"), 60);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "새 제목", null, null, null, null, null);

        var response = todoService.update(1L, "t1", request);

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.scheduledDates()).containsExactly("2026-04-16");
        assertThat(response.categoryIds()).containsExactly("c1");
        assertThat(response.estimatedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("update: 빈 배열은 명시적 모두 제거")
    void update_emptyArrayClears() {
        Todo existing = Todo.create("t1", 1L, "X", java.util.List.of("2026-04-16"),
                java.util.List.of("c1"), null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                null, java.util.List.of(), null, null, null, null);

        var response = todoService.update(1L, "t1", request);

        assertThat(response.scheduledDates()).isEmpty();
        assertThat(response.categoryIds()).containsExactly("c1");
    }

    @Test
    @DisplayName("update: 존재하지 않는 todoId → TODO_NOT_FOUND")
    void update_notFound() {
        when(todoRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(java.util.Optional.empty());

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "X", null, null, null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.update(1L, "missing", request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    @DisplayName("update: categoryIds 변경 시 검증")
    void update_categoryIdsValidated() {
        Todo existing = Todo.create("t1", 1L, "X", null, null, null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.countByIdInAndUserId(java.util.List.of("missing"), 1L))
                .thenReturn(0L);

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                null, null, null, java.util.List.of("missing"), null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.update(1L, "t1", request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.CATEGORY_NOT_FOUND);
    }
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: FAIL with "update not defined"

- [ ] **Step 3: TodoService.update 구현**

Append to `TodoService.java` (클래스 마지막 `}` 직전):

```java
    @Transactional
    public TodoResponse update(Long userId, String todoId,
                               com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new com.elipair.spacestudyship.common.exception.CustomException(
                        com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND));

        if (request.categoryIds() != null) {
            validateCategoryIds(userId, request.categoryIds());
            todo.updateCategoryIds(request.categoryIds());
        }
        if (request.title() != null) todo.updateTitle(request.title());
        if (request.scheduledDates() != null) todo.updateScheduledDates(request.scheduledDates());
        if (request.completedDates() != null) todo.updateCompletedDates(request.completedDates());
        if (request.estimatedMinutes() != null) todo.updateEstimatedMinutes(request.estimatedMinutes());
        if (request.actualMinutes() != null) todo.updateActualMinutes(request.actualMinutes());

        log.info("[Todo] 수정 | userId={}, todoId={}", userId, todoId);
        return TodoResponse.from(todo);
    }
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: BUILD SUCCESSFUL, 11 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoService.update (PATCH partial) #24"
```

---

## Task 14: TodoService.delete

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가 — delete 2개 케이스**

Append:

```java
    @Test
    @DisplayName("delete: 본인 Todo 삭제 성공")
    void delete_success() {
        when(todoRepository.existsByIdAndUserId("t1", 1L)).thenReturn(true);

        todoService.delete(1L, "t1");

        verify(todoRepository).deleteById("t1");
    }

    @Test
    @DisplayName("delete: 존재하지 않으면 TODO_NOT_FOUND")
    void delete_notFound() {
        when(todoRepository.existsByIdAndUserId("missing", 1L)).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.delete(1L, "missing"))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: FAIL with "delete not defined"

- [ ] **Step 3: TodoService.delete 구현**

Append to `TodoService.java`:

```java
    @Transactional
    public void delete(Long userId, String todoId) {
        if (!todoRepository.existsByIdAndUserId(todoId, userId)) {
            throw new com.elipair.spacestudyship.common.exception.CustomException(
                    com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
        }
        todoRepository.deleteById(todoId);
        log.info("[Todo] 삭제 | userId={}, todoId={}", userId, todoId);
    }
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoServiceTest"`
Expected: BUILD SUCCESSFUL, 13 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoService.delete #24"
```

---

## Task 15: TodoCategoryService.findAll/create/update

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`
- Test: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

Create:

```java
package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.todo.dto.CategoryCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryUpdateRequest;
import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoCategoryServiceTest {

    @Mock TodoCategoryRepository categoryRepository;
    @Mock TodoRepository todoRepository;
    @InjectMocks TodoCategoryService categoryService;

    @Test
    @DisplayName("findAll: 사용자 카테고리 목록 반환")
    void findAll() {
        when(categoryRepository.findByUserIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(TodoCategory.create("c1", 1L, "수학", null, null, null)));

        List<CategoryResponse> result = categoryService.findAll(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 서버 UUID 생성")
    void create_serverId() {
        var request = new CategoryCreateRequest(null, "수학", "math_icon", 0.3, 0.5);
        when(categoryRepository.existsById(anyString())).thenReturn(false);
        when(categoryRepository.save(any(TodoCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = categoryService.create(1L, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.name()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 동일 ID 있으면 CATEGORY_ALREADY_EXISTS")
    void create_duplicate() {
        var request = new CategoryCreateRequest("c1", "수학", null, null, null);
        when(categoryRepository.existsById("c1")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("update: 이름 변경 + 위치 변경 + iconId 유지")
    void update_partial() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "원본", "icon", 0.3, 0.5);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        var request = new CategoryUpdateRequest("새이름", null, 0.7, null);
        var response = categoryService.update(1L, "c1", request);

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.iconId()).isEqualTo("icon");
        assertThat(response.positionX()).isEqualTo(0.7);
        assertThat(response.positionY()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("update: 존재하지 않으면 CATEGORY_NOT_FOUND")
    void update_notFound() {
        when(categoryRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(Optional.empty());

        var request = new CategoryUpdateRequest("X", null, null, null);

        assertThatThrownBy(() -> categoryService.update(1L, "missing", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryServiceTest"`
Expected: FAIL with "cannot find symbol class TodoCategoryService"

- [ ] **Step 3: TodoCategoryService 구현 (findAll, create, update)**

Create:

```java
package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.todo.dto.CategoryCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryUpdateRequest;
import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoCategoryService {

    private final TodoCategoryRepository categoryRepository;
    private final TodoRepository todoRepository;

    public List<CategoryResponse> findAll(Long userId) {
        return categoryRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (categoryRepository.existsById(id)) {
            throw new CustomException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }
        TodoCategory category = TodoCategory.create(
                id, userId, request.name(),
                request.iconId(), request.positionX(), request.positionY());
        TodoCategory saved = categoryRepository.save(category);
        log.info("[TodoCategory] 생성 | userId={}, categoryId={}", userId, saved.getId());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, String categoryId, CategoryUpdateRequest request) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        if (request.name() != null) category.updateName(request.name());
        if (request.iconId() != null) category.updateIconId(request.iconId());
        if (request.positionX() != null) category.updatePositionX(request.positionX());
        if (request.positionY() != null) category.updatePositionY(request.positionY());
        log.info("[TodoCategory] 수정 | userId={}, categoryId={}", userId, categoryId);
        return CategoryResponse.from(category);
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryServiceTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoCategoryService 추가 (findAll/create/update) #24"
```

---

## Task 16: TodoCategoryService.delete (cascade Todo)

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

Append to `TodoCategoryServiceTest.java`:

```java
    @Test
    @DisplayName("delete: 카테고리 삭제 + 연관 Todo의 categoryIds에서 제거")
    void delete_cascadesToTodos() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "수학", null, null, null);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        com.elipair.spacestudyship.study.todo.entity.Todo t1 =
                com.elipair.spacestudyship.study.todo.entity.Todo.create(
                        "t1", 1L, "X", null, List.of("c1", "c2"), null);
        com.elipair.spacestudyship.study.todo.entity.Todo t2 =
                com.elipair.spacestudyship.study.todo.entity.Todo.create(
                        "t2", 1L, "Y", null, List.of("c1"), null);
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(t1, t2));

        categoryService.delete(1L, "c1");

        // 연관 Todo categoryIds에서 c1 제거 확인 (dirty checking)
        assertThat(t1.getCategoryIds()).containsExactly("c2");
        assertThat(t2.getCategoryIds()).isEmpty();
        // 카테고리 row 삭제 호출 확인
        org.mockito.Mockito.verify(categoryRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: 존재하지 않으면 CATEGORY_NOT_FOUND")
    void delete_notFound() {
        when(categoryRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(1L, "missing"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryServiceTest"`
Expected: FAIL with "delete not defined"

- [ ] **Step 3: TodoCategoryService.delete 구현**

Append to `TodoCategoryService.java` (클래스 마지막 `}` 직전):

```java
    @Transactional
    public void delete(Long userId, String categoryId) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        List<com.elipair.spacestudyship.study.todo.entity.Todo> affected =
                todoRepository.findByUserIdAndCategoryId(userId, "\"" + categoryId + "\"");
        affected.forEach(todo -> todo.removeCategoryId(categoryId));
        // dirty checking으로 categoryIds 변경 자동 반영

        categoryRepository.delete(category);
        log.info("[TodoCategory] 삭제 | userId={}, categoryId={}, affectedTodos={}",
                userId, categoryId, affected.size());
    }
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Study:test --tests "*TodoCategoryServiceTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoCategoryService.delete (연관 Todo categoryIds 정리) #24"
```

---

## Task 17: TodoController + Swagger + MockMvc

**Files:**
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/TodoController.java`
- Test: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoControllerTest.java`

- [ ] **Step 1: 실패하는 MockMvc 테스트 작성 (4개 엔드포인트)**

기존 `AuthControllerTest`의 패턴 (인증 mock, MockMvc) 확인 후 그대로 적용. Create:

```java
package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMemberArgumentResolver;
import com.elipair.spacestudyship.auth.interceptor.AuthInterceptor;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TodoControllerTest {

    @Mock TodoService todoService;
    @InjectMocks TodoController todoController;

    MockMvc mockMvc;
    ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // @AuthMember LoginMember 를 항상 memberId=1L 로 주입하는 stub resolver
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
        mockMvc = MockMvcBuilders.standaloneSetup(todoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .build();
    }

    @Test
    @DisplayName("GET /api/todos — 200")
    void findAll() throws Exception {
        when(todoService.findAll(eq(1L), eq(null), eq(null)))
                .thenReturn(List.of(new TodoResponse("t1", "수학",
                        List.of(), List.of(), List.of(), null, null,
                        "2026-05-23T00:00:00Z", "2026-05-23T00:00:00Z")));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"));
    }

    @Test
    @DisplayName("POST /api/todos — 201")
    void create() throws Exception {
        when(todoService.create(eq(1L), any()))
                .thenReturn(new TodoResponse("t1", "수학",
                        List.of(), List.of(), List.of(), null, null,
                        "2026-05-23T00:00:00Z", "2026-05-23T00:00:00Z"));

        String body = """
                {"id":"t1","title":"수학","categoryIds":[],"scheduledDates":[]}
                """;

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("t1"));
    }

    @Test
    @DisplayName("PATCH /api/todos/{id} — 404 TODO_NOT_FOUND")
    void update_notFound() throws Exception {
        when(todoService.update(eq(1L), eq("missing"), any()))
                .thenThrow(new CustomException(ErrorCode.TODO_NOT_FOUND));

        mockMvc.perform(patch("/api/todos/missing")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/todos/{id} — 204")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/todos/t1"))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Web:test --tests "*TodoControllerTest"`
Expected: FAIL with "cannot find symbol class TodoController"

- [ ] **Step 3: TodoController 구현 (Swagger 풀세트)**

Create `SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/TodoController.java`:

```java
package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Todo", description = "할 일 CRUD API")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Validated  // PathVariable/RequestParam의 @Pattern, @Min 등 검증 활성화
public class TodoController {

    private final TodoService todoService;

    @Operation(summary = "할 일 목록 조회",
            description = "선택적으로 date / categoryId 쿼리로 필터. 결과는 createdAt 내림차순.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = TodoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}")))
    })
    @GetMapping
    public ResponseEntity<List<TodoResponse>> findAll(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date: YYYY-MM-DD 형식이어야 합니다.")
            String date,
            @RequestParam(required = false)
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId) {
        return ResponseEntity.ok(todoService.findAll(loginMember.memberId(), date, categoryId));
    }

    @Operation(summary = "할 일 생성",
            description = """
                    새 할 일을 생성합니다. id 미지정 시 서버가 UUID v4 생성.

                    ### 동작
                    1. id 충돌 검사 → 충돌 시 409 TODO_ALREADY_EXISTS
                    2. categoryIds 실존 검증 → 누락 시 404 CATEGORY_NOT_FOUND
                    3. 저장 후 생성된 객체 반환
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = TodoResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"title: 비어있을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "동일 ID 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_ALREADY_EXISTS\",\"message\":\"동일 ID의 할 일이 이미 존재합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PostMapping
    public ResponseEntity<TodoResponse> create(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid TodoCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoService.create(loginMember.memberId(), request));
    }

    @Operation(summary = "할 일 부분 수정",
            description = "전송하지 않은 필드는 기존 값 유지. 빈 배열은 명시적 모두 제거.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = TodoResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"...\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "Todo 없음 또는 다른 사용자 소유",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PatchMapping("/{todoId}")
    public ResponseEntity<TodoResponse> update(
            @AuthMember LoginMember loginMember,
            @org.springframework.web.bind.annotation.PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "todoId: 영숫자와 하이픈만 허용합니다.")
            String todoId,
            @RequestBody @Valid TodoUpdateRequest request) {
        return ResponseEntity.ok(todoService.update(loginMember.memberId(), todoId, request));
    }

    @Operation(summary = "할 일 삭제", description = "본인 소유 Todo만 삭제 가능. 다른 사용자 / 없는 Todo는 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "Todo 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @DeleteMapping("/{todoId}")
    public ResponseEntity<Void> delete(
            @AuthMember LoginMember loginMember,
            @org.springframework.web.bind.annotation.PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "todoId: 영숫자와 하이픈만 허용합니다.")
            String todoId) {
        todoService.delete(loginMember.memberId(), todoId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Web:test --tests "*TodoControllerTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/TodoController.java SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoControllerTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoController 추가 (Swagger 풀세트) #24"
```

---

## Task 18: TodoCategoryController + Swagger + MockMvc

**Files:**
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/TodoCategoryController.java`
- Test: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoCategoryControllerTest.java`

- [ ] **Step 1: 실패하는 MockMvc 테스트 작성**

Create:

```java
package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.service.TodoCategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TodoCategoryControllerTest {

    @Mock TodoCategoryService categoryService;
    @InjectMocks TodoCategoryController categoryController;

    MockMvc mockMvc;
    ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver stub = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return p.getParameterType().equals(LoginMember.class);
            }
            @Override public Object resolveArgument(MethodParameter p,
                    ModelAndViewContainer m,
                    org.springframework.web.context.request.NativeWebRequest w,
                    org.springframework.web.bind.support.WebDataBinderFactory f) {
                return new LoginMember(1L);
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(stub)
                .build();
    }

    @Test
    @DisplayName("GET /api/todo-categories — 200")
    void findAll() throws Exception {
        when(categoryService.findAll(1L)).thenReturn(List.of(
                new CategoryResponse("c1", "수학", "math", 0.3, 0.5,
                        "2026-05-23T00:00:00Z", null)));

        mockMvc.perform(get("/api/todo-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("c1"));
    }

    @Test
    @DisplayName("POST /api/todo-categories — 201")
    void create() throws Exception {
        when(categoryService.create(eq(1L), any()))
                .thenReturn(new CategoryResponse("c1", "수학", null, null, null,
                        "2026-05-23T00:00:00Z", null));

        String body = "{\"id\":\"c1\",\"name\":\"수학\"}";

        mockMvc.perform(post("/api/todo-categories")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    @DisplayName("PATCH /api/todo-categories/{id} — 404 CATEGORY_NOT_FOUND")
    void update_notFound() throws Exception {
        when(categoryService.update(eq(1L), eq("missing"), any()))
                .thenThrow(new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(patch("/api/todo-categories/missing")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/todo-categories/{id} — 204")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/todo-categories/c1"))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

Run: `./gradlew :SS-Web:test --tests "*TodoCategoryControllerTest"`
Expected: FAIL with "cannot find symbol class TodoCategoryController"

- [ ] **Step 3: TodoCategoryController 구현 (Swagger 풀세트)**

Create:

```java
package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryUpdateRequest;
import com.elipair.spacestudyship.study.todo.service.TodoCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "TodoCategory", description = "할 일 카테고리 CRUD API")
@RestController
@RequestMapping("/api/todo-categories")
@RequiredArgsConstructor
@Validated  // PathVariable의 @Pattern 검증 활성화
public class TodoCategoryController {

    private final TodoCategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "createdAt 오름차순")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class)))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}")))
    })
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(categoryService.findAll(loginMember.memberId()));
    }

    @Operation(summary = "카테고리 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"name: 비어있을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "동일 ID 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_ALREADY_EXISTS\",\"message\":\"동일 ID의 카테고리가 이미 존재합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(loginMember.memberId(), request));
    }

    @Operation(summary = "카테고리 부분 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"...\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryService.update(loginMember.memberId(), categoryId, request));
    }

    @Operation(summary = "카테고리 삭제",
            description = "삭제 시 연관 Todo의 categoryIds에서 자동 제거됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId) {
        categoryService.delete(loginMember.memberId(), categoryId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `./gradlew :SS-Web:test --tests "*TodoCategoryControllerTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/TodoCategoryController.java SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoCategoryControllerTest.java
git commit -m "할일 및 카테고리 도메인 구현 : feat : TodoCategoryController 추가 (Swagger 풀세트) #24"
```

---

## Task 19: CLAUDE.md 이력 업데이트 + 최종 검증

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: CLAUDE.md 마이그레이션 이력 표 업데이트**

CLAUDE.md의 "현재 마이그레이션 이력" 표에 새 행 추가:

```markdown
### 현재 마이그레이션 이력

| 버전 | 파일 | 내용 |
|------|------|------|
| 0.0.31 | `V0_0_31__add_user_devices.sql` | 초기 스키마 — `members`, `user_devices` 테이블 생성 (FK 포함) |
| 0.0.34 | `V0_0_34__add_todos_and_categories.sql` | `todos`, `todo_categories` 테이블 생성 (FK CASCADE, JSONB 컬럼) |
```

- [ ] **Step 2: 전체 빌드 + 전체 테스트 실행**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. 모든 모듈 테스트 통과.

만약 실패하면:
- Docker 데몬 실행 중인지 확인 (Testcontainers 필요)
- 컴파일 에러 확인 후 해당 task로 돌아가 수정
- 다른 모듈의 테스트가 회귀했는지 확인

- [ ] **Step 3: 운영 환경 가정 — Application 컨텍스트 로딩 확인**

Run: `./gradlew :SS-Web:bootJarMainClassName` (jar 메인 클래스 검증) 또는 단순 컴파일:
`./gradlew :SS-Web:compileJava`
Expected: SUCCESS. Controller가 Application 컨텍스트에 인식되는지 확인.

- [ ] **Step 4: Swagger UI 수동 검증 (선택)**

로컬에서 Spring Boot 실행 후 `http://localhost:8080/swagger-ui.html` 접속하여:
- `Todo` 태그 그룹에 4개 엔드포인트 존재
- `TodoCategory` 태그 그룹에 4개 엔드포인트 존재
- 각 엔드포인트의 응답 예시(200/201/204/400/401/404/409/500) 표시됨
- DTO 스키마 (TodoResponse, CategoryResponse 등) 표시됨

Docker / DB 셋업 불가하면 이 step은 건너뜀.

- [ ] **Step 5: 최종 커밋**

```bash
git add CLAUDE.md
git commit -m "할일 및 카테고리 도메인 구현 : docs : CLAUDE.md 마이그레이션 이력 업데이트 #24"
```

---

## Self-Review Notes

**Spec coverage 검증:**

| Spec 섹션 | Task |
|-----------|------|
| 1. 개요 & 범위 | 전체 |
| 2. 모듈/패키지 구조 | Task 3~18 |
| 3. Entity 설계 (Todo) | Task 8 |
| 3. Entity 설계 (TodoCategory) | Task 5 |
| 4. DTO 설계 (Todo DTO) | Task 10 |
| 4. DTO 설계 (Category DTO) | Task 7 |
| 4.3 PATCH null vs 빈 배열 규약 | Task 13 (Todo), Task 15 (Category) |
| 5. Repository (Todo, jsonb @>) | Task 9 |
| 5. Repository (Category) | Task 6 |
| 6.1 TodoService 핵심 로직 | Task 11~14 |
| 6.2 TodoCategoryService 핵심 로직 | Task 15~16 |
| 7. Controller + Swagger (Todo) | Task 17 |
| 7. Controller + Swagger (Category) | Task 18 |
| 8. ErrorCode 추가 | Task 1 |
| 9. Flyway 마이그레이션 + version.yml | Task 2 |
| 10. 테스트 전략 | Task 3~4 셋업, 5~18 각 단계 TDD |
| 11. 셀프 리뷰 체크리스트 | Task 17~19 (수동 확인) |
| 12. 작업 산출물 요약 | Task 19 종합 |

**검토 결과:** 모든 spec 항목에 대응 task 존재. Type 일관성 — `TodoResponse.from(Todo)`, `CategoryResponse.from(TodoCategory)`, `Todo.removeCategoryId(String)`, `LoginMember.memberId()` 등 task 간 식별자 일관성 유지.

**리스크:**
- **Testcontainers Docker 의존성**: Task 4, 6, 9 실행 시 Docker 데몬 필요. CI 환경에서는 별도 setup 필요할 수 있음.
- **Hibernate 6 JSONB 매핑**: 운영 PostgreSQL과 테스트 Testcontainers의 PostgreSQL 버전 호환성 (Postgres 16 사용).
- **PathVariable의 `@Pattern`**: Spring 환경에서 `@Valid`가 없으면 PathVariable validation이 동작하지 않음 → `@Validated` 어노테이션을 Controller 클래스에 추가 필요. 구현 시 이 점 추가 확인 (만약 Task 17/18 실행 시 path validation이 동작 안 하면 Controller 클래스에 `@org.springframework.validation.annotation.Validated` 추가).
