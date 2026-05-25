# Todo / TodoCategory 응답 timestamps 보장 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Todo / TodoCategory 응답의 `createdAt`/`updatedAt`이 항상 ISO-8601 문자열로 채워져 내려가도록 보장한다 (현재 null로 내려가서 Flutter 앱이 `_TypeError`로 죽음).

**Architecture:** assigned-ID 패턴 두 엔티티(Todo, TodoCategory)에 `Persistable<String>`을 구현해 Spring Data JPA가 `merge()` 대신 `persist()`를 호출하도록 한다 (→ `@CreationTimestamp` 즉시 채워짐). update 경로에는 `EntityManager.flush()`를 추가해 dirty checking 결과를 응답 전에 강제 반영한다.

**Tech Stack:** Java 21, Spring Boot 4.0.2, Hibernate (`@CreationTimestamp`/`@UpdateTimestamp` via `BaseTimeEntity`), Gradle 멀티모듈 (`SS-Common`, `SS-Study`, `SS-Web`), JUnit 5 + Mockito + AssertJ, Postgres + Flyway, JSONB 컬럼.

**Spec:** `docs/superpowers/specs/2026-05-25-todo-timestamps-design.md`

---

## 사전 준비

- [ ] **Step 0.1: 작업 디렉토리 확인**

```bash
pwd
# 기대: /Users/luca/workspace/Java_Spring/space_study_ship
git branch --show-current
# 현재 브랜치 메모 (이번 작업도 같은 브랜치에 누적)
```

- [ ] **Step 0.2: 베이스라인 빌드 통과 확인**

```bash
./gradlew :SS-Study:test :SS-Web:test
# 기대: BUILD SUCCESSFUL (이번 작업 전 회귀 베이스라인)
```

만약 베이스라인이 깨져 있으면 본 계획 진행 전 사용자에게 알리고 멈춘다.

---

## Task 1 — Todo entity에 `Persistable<String>` 구현

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java`
- Test (new test method, append): `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java`

### TDD 사이클

- [ ] **Step 1.1: 실패할 통합 테스트 작성**

`TodoRepositoryTest`의 클래스 안에 다음 테스트 메서드 한 개를 추가한다 (마지막 `}` 닫힘 직전):

```java
@Test
@DisplayName("save: assigned-ID Todo도 createdAt/updatedAt 즉시 채워짐 (Persistable)")
void save_persistsTimestampsForAssignedId() {
    Todo saved = todoRepository.save(Todo.create("t-ts", 1L, "X", null, null, null));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
}
```

(import는 기존 파일에서 `assertThat`, `Todo` 모두 이미 가져옴 — 추가 import 불필요)

- [ ] **Step 1.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoRepositoryTest.save_persistsTimestampsForAssignedId"
```

기대: `expected: not null but was: null` AssertionFailedError. `save()`가 `merge()`를 호출해 INSERT가 flush까지 지연 → entity의 `createdAt`이 null. **이 실패는 root cause를 정확히 재현**한다.

만약 PASS가 떠버리면 (`@SpringBootTest` 클래스 위의 `@Transactional` 동작 차이로 hibernate가 즉시 flush할 수도 있음) 진단이 달라지므로 멈추고 사용자에게 보고한다.

- [ ] **Step 1.3: Todo에 `Persistable<String>` 구현 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java`를 수정:

1. import 한 줄 추가:
```java
import org.springframework.data.domain.Persistable;
```

2. 클래스 선언 변경:
```java
public class Todo extends BaseTimeEntity implements Persistable<String> {
```

3. 클래스 내부 마지막 메서드(`removeCategoryId`) 다음, 닫는 `}` 직전에 다음 두 메서드 추가:

```java
    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
```

`getId()`는 Lombok `@Getter`가 만든 것과 동일 시그니처지만, 인터페이스 명시를 위해 명시적으로 override.

- [ ] **Step 1.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoRepositoryTest.save_persistsTimestampsForAssignedId"
```

기대: BUILD SUCCESSFUL. `Persistable.isNew()`가 true 반환 → Spring Data가 `persist()` 호출 → PrePersist에서 `@CreationTimestamp`/`@UpdateTimestamp` 즉시 채워짐.

- [ ] **Step 1.5: 회귀 확인 — Todo 관련 테스트 전체 실행**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL. 기존 Repository / Service / Entity 테스트 모두 통과 (Persistable 구현이 기존 동작을 깨면 안 됨).

- [ ] **Step 1.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : Todo 엔티티에 Persistable<String> 구현으로 INSERT 즉시 발생 보장"
```

---

## Task 2 — TodoCategory entity에 `Persistable<String>` 구현

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java`
- Test (new test method, append): `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java`

### TDD 사이클

- [ ] **Step 2.1: 실패할 통합 테스트 작성**

`TodoCategoryRepositoryTest` 클래스 안에 다음 테스트 추가:

```java
@Test
@DisplayName("save: assigned-ID TodoCategory도 createdAt/updatedAt 즉시 채워짐 (Persistable)")
void save_persistsTimestampsForAssignedId() {
    TodoCategory saved = categoryRepository.save(
            TodoCategory.create("c-ts", 1L, "수학", null, null, null));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
}
```

(필요 import 이미 존재)

- [ ] **Step 2.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepositoryTest.save_persistsTimestampsForAssignedId"
```

기대: AssertionFailedError. Task 1과 동일한 root cause.

- [ ] **Step 2.3: TodoCategory에 `Persistable<String>` 구현 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java`를 수정:

1. import 한 줄 추가:
```java
import org.springframework.data.domain.Persistable;
```

2. 클래스 선언 변경:
```java
public class TodoCategory extends BaseTimeEntity implements Persistable<String> {
```

3. 클래스 내부 마지막 메서드(`updatePositionY`) 다음, 닫는 `}` 직전에 다음 두 메서드 추가:

```java
    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
```

- [ ] **Step 2.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepositoryTest.save_persistsTimestampsForAssignedId"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 2.5: 회귀 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 2.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : TodoCategory 엔티티에 Persistable<String> 구현"
```

---

## Task 3 — `TodoService.update()`에 `EntityManager.flush()` 추가

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

### 변경 의도

update()는 managed entity를 dirty checking으로 갱신하는데, 응답 직전에 `flush()`를 호출하지 않으면 `@UpdateTimestamp`가 채워지지 않아 응답의 `updatedAt`이 stale 값으로 나간다. `EntityManager`를 service에 주입하고 mutation 직후 `flush()` 호출.

### TDD 사이클

- [ ] **Step 3.1: 실패할 단위 테스트 작성 (flush 호출 검증)**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`를 수정한다.

(a) imports 영역에 다음 추가:

```java
import jakarta.persistence.EntityManager;
```

그리고 mockito 정적 import 영역(`import static org.mockito.Mockito.when;` 옆)에 다음 줄이 없으면 추가:

```java
import static org.mockito.Mockito.verify;   // 이미 있음 — 확인만
```

(b) 클래스 필드 `@InjectMocks TodoService todoService;` 위에 mock 한 개 추가:

```java
    @Mock EntityManager entityManager;
```

(c) 클래스 안에 새 테스트 추가 (마지막 닫는 `}` 직전):

```java
    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 보장")
    void update_flushesAfterMutation() {
        Todo existing = Todo.create("t1", 1L, "원본", null, null, null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "새 제목", null, null, null, null, null);

        todoService.update(1L, "t1", request);

        verify(entityManager).flush();
    }
```

- [ ] **Step 3.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.update_flushesAfterMutation"
```

기대 (둘 중 하나):
- 컴파일 에러: `EntityManager`가 TodoService 생성자에 없음 (Mockito가 inject 못 함 — 단 mock 필드만 있을 땐 컴파일은 통과, runtime에서 verify가 NeverWantedButInvoked로 실패)
- 또는 `Wanted but not invoked: entityManager.flush();`

- [ ] **Step 3.3: `TodoService`에 EntityManager 주입 + flush 호출 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`를 수정.

(a) imports 영역에 추가:

```java
import jakarta.persistence.EntityManager;
```

(b) 필드 영역에 추가 (`private final TodoCategoryRepository categoryRepository;` 다음 줄):

```java
    private final EntityManager entityManager;
```

(c) `update()` 메서드 마지막 `log.info(...)` 직전 (또는 직후, 어쨌든 `return TodoResponse.from(todo);` 직전)에 한 줄 추가:

```java
        entityManager.flush();
```

최종 메서드 모양 (참고):

```java
    @Transactional
    public TodoResponse update(Long userId, String todoId,
                               com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        if (request.categoryIds() != null) {
            validateCategoryIds(userId, request.categoryIds());
            todo.updateCategoryIds(request.categoryIds());
        }
        if (request.title() != null) todo.updateTitle(request.title());
        if (request.scheduledDates() != null) todo.updateScheduledDates(request.scheduledDates());
        if (request.completedDates() != null) todo.updateCompletedDates(request.completedDates());
        if (request.estimatedMinutes() != null) todo.updateEstimatedMinutes(request.estimatedMinutes());
        if (request.actualMinutes() != null) todo.updateActualMinutes(request.actualMinutes());

        entityManager.flush();
        log.info("[Todo] 수정 | userId={}, todoId={}", userId, todoId);
        return TodoResponse.from(todo);
    }
```

- [ ] **Step 3.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.update_flushesAfterMutation"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 3.5: 회귀 확인 — TodoServiceTest 전체 + 통합 테스트**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL. 기존 update 관련 단위 테스트들(예: `update_titleOnly`, `update_emptyArrayClears`, `update_notFound`, `update_categoryIdsValidated`)이 모두 통과해야 한다 — `EntityManager` mock이 추가됐어도 동작은 동일.

만약 `update_notFound` 같은 케이스가 예외 던지기 전에 flush가 호출되어 실패하면, 그건 코드 배치 잘못이다. flush는 mutation 이후에만 호출되어야 함 (위 코드는 `orElseThrow` → mutation → flush 순서라 안전).

- [ ] **Step 3.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : TodoService.update()에 EntityManager.flush() 추가로 updatedAt 갱신 보장"
```

---

## Task 4 — `TodoCategoryService.update()`에 `EntityManager.flush()` 추가

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java`

### TDD 사이클

- [ ] **Step 4.1: 실패할 단위 테스트 작성**

`TodoCategoryServiceTest.java`를 수정.

(a) imports 추가:

```java
import jakarta.persistence.EntityManager;
import static org.mockito.Mockito.verify;
```

(`verify`가 기존 imports에 없다 — `Mockito.verify`는 본 파일에서 `org.mockito.Mockito.verify(categoryRepository).delete(existing);` 처럼 fully-qualified로 쓰여 있다. static import 추가하거나 새 테스트에서도 fully-qualified로 쓸 수 있음. 일관성을 위해 static import 추가 권장.)

(b) 클래스 필드에 mock 추가 (`@InjectMocks` 직전):

```java
    @Mock EntityManager entityManager;
```

(c) 클래스 안에 새 테스트 추가 (마지막 닫는 `}` 직전):

```java
    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 보장")
    void update_flushesAfterMutation() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "원본", "icon", 0.3, 0.5);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        var request = new CategoryUpdateRequest("새이름", null, null, null);
        categoryService.update(1L, "c1", request);

        verify(entityManager).flush();
    }
```

- [ ] **Step 4.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.update_flushesAfterMutation"
```

기대: `Wanted but not invoked: entityManager.flush();`

- [ ] **Step 4.3: `TodoCategoryService`에 EntityManager 주입 + flush 호출 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`를 수정.

(a) imports 추가:

```java
import jakarta.persistence.EntityManager;
```

(b) 필드 추가 (`private final TodoRepository todoRepository;` 다음 줄):

```java
    private final EntityManager entityManager;
```

(c) `update()` 메서드 안, `log.info(...)` 직전에 한 줄 추가:

```java
        entityManager.flush();
```

최종 메서드:

```java
    @Transactional
    public CategoryResponse update(Long userId, String categoryId, CategoryUpdateRequest request) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        if (request.name() != null) category.updateName(request.name());
        if (request.iconId() != null) category.updateIconId(request.iconId());
        if (request.positionX() != null) category.updatePositionX(request.positionX());
        if (request.positionY() != null) category.updatePositionY(request.positionY());
        entityManager.flush();
        log.info("[TodoCategory] 수정 | userId={}, categoryId={}", userId, categoryId);
        return CategoryResponse.from(category);
    }
```

- [ ] **Step 4.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.update_flushesAfterMutation"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 4.5: 회귀 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 4.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : TodoCategoryService.update()에 EntityManager.flush() 추가"
```

---

## Task 5 — 통합 검증 (전체 빌드 + 수동 curl)

**Files:** 없음 (검증만)

- [ ] **Step 5.1: 전체 테스트 실행**

```bash
./gradlew :SS-Common:test :SS-Auth:test :SS-Member:test :SS-Study:test :SS-Web:test
```

기대: BUILD SUCCESSFUL across all modules.

만약 SS-Web의 `TodoControllerTest` / `TodoCategoryControllerTest`가 깨졌다면 — 이 테스트들은 `@Mock TodoService` 사용이라 service 시그니처 변경(생성자에 EntityManager 추가) 영향 없음. 깨지면 다른 원인이므로 멈추고 사용자에게 보고.

- [ ] **Step 5.2: 서버 기동 → 수동 curl 검증 (사용자가 직접 수행)**

본 단계는 백엔드 빌드 결과를 실제 HTTP로 검증한다. 에이전트가 자동으로 실행하지 말고 사용자에게 다음을 안내한다:

```bash
./gradlew :SS-Web:bootRun
```

(다른 터미널에서)

```bash
TOKEN="<로그인 토큰>"

curl -s -X POST http://localhost:8080/api/todos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"11111111-1111-1111-1111-111111111111","title":"테스트","scheduledDates":["2026-05-25"]}' \
  | jq '{createdAt,updatedAt}'
# 기대: 둘 다 ISO-8601 UTC 문자열 (예: "2026-05-25T03:14:15.123Z"), null 아님

curl -s -X PATCH http://localhost:8080/api/todos/11111111-1111-1111-1111-111111111111 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"수정"}' \
  | jq '{createdAt,updatedAt}'
# 기대: createdAt 동일, updatedAt이 더 큰(또는 같은) 값
```

- [ ] **Step 5.3: Flutter 앱 회귀 확인 (사용자가 직접 수행)**

사용자에게 안내:
- 플러터 앱 재실행 → Todo 생성 → 더 이상 `_TypeError (type 'Null' is not a subtype of type 'String' in type cast)` 안 뜸
- 콘솔 로그에 `createdAt`/`updatedAt`이 ISO-8601 문자열로 찍히는지 확인

**5.2 / 5.3은 에이전트가 PASS로 표시하지 말고 사용자에게 확인을 받은 뒤 표시한다.**

- [ ] **Step 5.4: (선택) PR 본문 메모 준비**

PR을 생성한다면 본문에 한 줄 명시:
> Todo / TodoCategory 응답의 createdAt / updatedAt always-non-null 보장 — assigned-ID 패턴에서 `save()`가 `merge()`로 호출돼 flush 전 시간 필드가 null로 내려가던 결함 수정.

---

## 비범위 (Out of scope)

- FuelTransaction / UserFuel (같은 assigned-ID 패턴이지만 응답 노출 여부 확인 후 별도 이슈)
- BaseTimeEntity 자체에 Persistable 통합 (IDENTITY 엔티티 영향 회피)
- `docs/api-docs.json` 재생성 (Springdoc 자동생성이면 다음 빌드/배포 사이클에서 처리)

---

## Self-Review

본 계획을 spec(`docs/superpowers/specs/2026-05-25-todo-timestamps-design.md`)과 대조 점검한 결과:

**Spec coverage:**
- spec §3.1 변경 파일 8개 중 production 4개 ✅ Task 1–4에서 모두 커버
- spec §3.1 test 파일 4개 중 Repository 통합 테스트 2개(`TodoRepositoryTest`, `TodoCategoryRepositoryTest`) → Task 1.1 / 2.1에서 추가. Service 단위 테스트 2개(`TodoServiceTest`, `TodoCategoryServiceTest`) → Task 3.1 / 4.1에서 update flush verify 추가
- spec §3.1 controller 테스트 2개 → **본 계획에서는 추가하지 않음** (controller test가 `@Mock TodoService`로 service 결과를 stub하므로 실제 timestamp 동작 검증 불가 → spec의 §6.3 Spring Boot 통합 controller 테스트 추가는 가치 대비 보일러플레이트 큼). 진짜 검증은 Repository 통합 테스트(Task 1.1 / 2.1) + 수동 curl(Task 5.2)이 담당. 이는 spec의 §5.3 수동 검증으로 보장됨.
- spec §3.2 Persistable 패턴 ✅ Task 1.3 / 2.3에 동일 코드
- spec §3.3 service 패턴 ✅ Task 3.3 / 4.3에 동일 코드
- spec §5.3 수동 curl ✅ Task 5.2
- spec §5.4 Flutter 회귀 ✅ Task 5.3
- spec §6 위험 검토 — Task 1.5 / 2.5 / 3.5 / 4.5 회귀 확인 step으로 커버

**Placeholder scan:** TBD/TODO/"implement later" 등 없음. 모든 코드 블록에 실제 코드 포함.

**Type consistency:** `Persistable<String>`, `getId()` 시그니처, `isNew()` 시그니처, `EntityManager.flush()` 호출이 Task 1–4에서 모두 동일하게 사용됨. `getCreatedAt()` 호출은 `BaseTimeEntity`의 `@Getter`가 만든 메서드 — 두 entity 모두 동일.

**의식적 비범위:** Controller 통합 테스트는 spec §6.3에 언급됐지만 본 계획에서는 가치 대비 비용을 고려해 생략. Repository 통합 + 수동 검증으로 충분. 필요 시 후속 이슈로 추가 가능.
