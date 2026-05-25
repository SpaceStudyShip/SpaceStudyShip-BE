# Todo / TodoCategory 응답 timestamps 보장 구현 계획 (revised)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Todo / TodoCategory 응답의 `createdAt`/`updatedAt`이 항상 ISO-8601 문자열로 채워져 내려가도록 보장한다.

**Architecture:** `TodoService` / `TodoCategoryService`의 create / update 경로에 `EntityManager.flush()` 호출을 추가해 응답 직전에 Hibernate가 INSERT/UPDATE를 실행하고 `@CreationTimestamp` / `@UpdateTimestamp`를 채우도록 강제한다. Entity는 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.0.2, Hibernate (`@CreationTimestamp`/`@UpdateTimestamp` via `BaseTimeEntity`), Gradle 멀티모듈 (`SS-Common`, `SS-Study`, `SS-Web`), JUnit 5 + Mockito + AssertJ, Postgres + Flyway, JSONB 컬럼.

**Spec:** `docs/superpowers/specs/2026-05-25-todo-timestamps-design.md`

---

## 사전 준비

- [ ] **Step 0.1: 작업 디렉토리 + 베이스라인 확인**

```bash
pwd
# 기대: /Users/luca/workspace/Java_Spring/space_study_ship
git status --short
# 기대: 비어 있음 (clean working tree)
./gradlew :SS-Study:test
# 기대: BUILD SUCCESSFUL
```

베이스라인이 깨져 있거나 working tree가 dirty면 멈추고 controller에게 보고.

---

## Task 1 — `TodoService`에 EntityManager 주입 + create/update에 flush() 추가

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

### TDD 사이클

- [ ] **Step 1.1: 실패할 단위 테스트 2개 작성 (create + update)**

`TodoServiceTest.java`를 수정한다.

(a) imports 영역에 추가:

```java
import jakarta.persistence.EntityManager;
```

(b) 클래스 필드 `@InjectMocks TodoService todoService;` **직전**에 mock 추가:

```java
    @Mock EntityManager entityManager;
```

(c) 클래스 안에 새 테스트 2개 추가 (마지막 닫는 `}` 직전):

```java
    @Test
    @DisplayName("create: save 후 EntityManager.flush() 호출 — createdAt/updatedAt 보장")
    void create_flushesAfterSave() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t-new", "수학", java.util.List.of(), null, java.util.List.of("2026-05-25"));
        when(todoRepository.existsById("t-new")).thenReturn(false);
        when(todoRepository.save(org.mockito.ArgumentMatchers.any(Todo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        todoService.create(1L, request);

        verify(entityManager).flush();
    }

    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 갱신 보장")
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

- [ ] **Step 1.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.create_flushesAfterSave" --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.update_flushesAfterMutation"
```

기대: 둘 다 `Wanted but not invoked: entityManager.flush();` (Mockito assertion).

- [ ] **Step 1.3: `TodoService`에 EntityManager 주입 + flush() 호출 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`를 수정.

(a) imports 영역에 추가:

```java
import jakarta.persistence.EntityManager;
```

(b) 필드 영역에 추가 (`private final TodoCategoryRepository categoryRepository;` 다음 줄):

```java
    private final EntityManager entityManager;
```

(c) `create()` 메서드: `Todo saved = todoRepository.save(todo);` **다음 줄**에 추가:

```java
        entityManager.flush();
```

최종 `create()` 모양:

```java
    @Transactional
    public TodoResponse create(Long userId, TodoCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (todoRepository.existsById(id)) {
            throw new CustomException(ErrorCode.TODO_ALREADY_EXISTS);
        }
        validateCategoryIds(userId, request.categoryIds());

        Todo todo = Todo.create(
                id, userId, request.title(),
                request.scheduledDates(),
                request.categoryIds(),
                request.estimatedMinutes());
        Todo saved = todoRepository.save(todo);
        entityManager.flush();
        log.info("[Todo] 생성 | userId={}, todoId={}", userId, saved.getId());
        return TodoResponse.from(saved);
    }
```

(d) `update()` 메서드: 모든 `todo.updateXxx(...)` 호출 **다음**, `log.info(...)` **직전**에 추가:

```java
        entityManager.flush();
```

최종 `update()` 모양:

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

- [ ] **Step 1.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.create_flushesAfterSave" --tests "com.elipair.spacestudyship.study.todo.service.TodoServiceTest.update_flushesAfterMutation"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 1.5: TodoServiceTest 전체 회귀 + Todo 모듈 회귀 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL. 기존 테스트(create/update/delete/findAll 등)들이 `EntityManager` mock 추가 영향 없이 모두 통과해야 한다 — `entityManager.flush()`는 mock에서 no-op이므로 동작 동일.

특히 `update_notFound`가 통과해야 함 — `entityManager.flush()`는 `orElseThrow` 이후에 호출되므로 예외 케이스에선 도달하지 않음.

- [ ] **Step 1.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : TodoService create/update에 EntityManager.flush() 추가로 응답 직전 timestamp 채워짐 보장"
```

CLAUDE.md 규칙: 메시지 형식 `{이슈제목} : {type} : {설명}`, 이모지 금지, Co-Authored-By 금지.

---

## Task 2 — `TodoCategoryService`에 동일 패턴 적용

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java`

### TDD 사이클

- [ ] **Step 2.1: 실패할 단위 테스트 2개 작성**

`TodoCategoryServiceTest.java`를 수정.

(a) imports 추가:

```java
import jakarta.persistence.EntityManager;
import static org.mockito.Mockito.verify;
```

기존 `org.mockito.Mockito.verify(...)`를 fully-qualified로 호출하는 라인이 있어도 static import는 안전하게 공존 가능. 새 테스트에서는 짧은 `verify` 사용.

(b) 클래스 필드에 mock 추가 (`@InjectMocks` 직전):

```java
    @Mock EntityManager entityManager;
```

(c) 클래스 안에 새 테스트 2개 추가 (마지막 닫는 `}` 직전):

```java
    @Test
    @DisplayName("create: save 후 EntityManager.flush() 호출 — createdAt/updatedAt 보장")
    void create_flushesAfterSave() {
        var request = new CategoryCreateRequest("c-new", "수학", "math_icon", 0.3, 0.5);
        when(categoryRepository.existsById("c-new")).thenReturn(false);
        when(categoryRepository.save(any(TodoCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(1L, request);

        verify(entityManager).flush();
    }

    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 갱신 보장")
    void update_flushesAfterMutation() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "원본", "icon", 0.3, 0.5);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        var request = new CategoryUpdateRequest("새이름", null, null, null);
        categoryService.update(1L, "c1", request);

        verify(entityManager).flush();
    }
```

- [ ] **Step 2.2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.create_flushesAfterSave" --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.update_flushesAfterMutation"
```

기대: 둘 다 `Wanted but not invoked: entityManager.flush();`.

- [ ] **Step 2.3: `TodoCategoryService`에 EntityManager 주입 + flush() 호출 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java`를 수정.

(a) imports 추가:

```java
import jakarta.persistence.EntityManager;
```

(b) 필드 추가 (`private final TodoRepository todoRepository;` 다음 줄):

```java
    private final EntityManager entityManager;
```

(c) `create()` 메서드: `TodoCategory saved = categoryRepository.save(category);` 다음 줄에 추가:

```java
        entityManager.flush();
```

(d) `update()` 메서드: 모든 `category.updateXxx(...)` 호출 다음, `log.info(...)` 직전에 추가:

```java
        entityManager.flush();
```

최종 메서드 모양:

```java
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
        entityManager.flush();
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
        entityManager.flush();
        log.info("[TodoCategory] 수정 | userId={}, categoryId={}", userId, categoryId);
        return CategoryResponse.from(category);
    }
```

`delete()`는 변경 없음.

- [ ] **Step 2.4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.create_flushesAfterSave" --tests "com.elipair.spacestudyship.study.todo.service.TodoCategoryServiceTest.update_flushesAfterMutation"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 2.5: 회귀 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 2.6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : fix : TodoCategoryService create/update에 EntityManager.flush() 추가"
```

---

## Task 3 — Repository 통합 회귀 테스트 추가 (Hibernate flush-time timestamp 동작 알람)

**Files:**
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java`

이 task의 목적은 production 동작 변경이 아니라 **Hibernate 버전 업그레이드 등으로 flush-time `@CreationTimestamp` 동작이 바뀌면 즉시 알람**을 받기 위함.

- [ ] **Step 3.1: TodoRepositoryTest에 회귀 테스트 추가**

`TodoRepositoryTest` 클래스 안에 추가:

```java
@Test
@DisplayName("saveAndFlush: assigned-ID Todo의 timestamp가 flush 후 채워짐 (Hibernate 회귀 알람)")
void saveAndFlush_populatesTimestamps() {
    Todo saved = todoRepository.saveAndFlush(Todo.create("t-ts", 1L, "X", null, null, null));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
}
```

(imports는 기존에 모두 존재)

- [ ] **Step 3.2: TodoCategoryRepositoryTest에 동일 추가**

```java
@Test
@DisplayName("saveAndFlush: assigned-ID TodoCategory의 timestamp가 flush 후 채워짐 (Hibernate 회귀 알람)")
void saveAndFlush_populatesTimestamps() {
    TodoCategory saved = categoryRepository.saveAndFlush(
            TodoCategory.create("c-ts", 1L, "수학", null, null, null));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
}
```

- [ ] **Step 3.3: 두 테스트 모두 PASS 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoRepositoryTest.saveAndFlush_populatesTimestamps" --tests "com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepositoryTest.saveAndFlush_populatesTimestamps"
```

기대: 둘 다 PASS. 이전 implementer diagnostic에서 saveAndFlush로 PASS 확인됨.

만약 FAIL이면 Hibernate timestamp 메커니즘 자체가 달라진 것 → 멈추고 controller에게 보고.

- [ ] **Step 3.4: 회귀 확인**

```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.*"
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 3.5: 커밋**

```bash
git add SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java
git commit -m "Todo 응답 createdAt/updatedAt null 반환 문제 : test : Hibernate flush-time timestamp 동작 회귀 알람 테스트 추가"
```

---

## Task 4 — 통합 검증 (전체 빌드 + 수동 curl)

**Files:** 없음 (검증만)

- [ ] **Step 4.1: 전체 테스트 실행**

```bash
./gradlew :SS-Common:test :SS-Auth:test :SS-Member:test :SS-Study:test :SS-Web:test
```

기대: BUILD SUCCESSFUL across all modules.

SS-Web의 controller 테스트는 `@Mock TodoService` 사용이라 service 시그니처 변경 영향 없음. 만약 깨지면 다른 원인이므로 멈추고 controller에게 보고.

- [ ] **Step 4.2: 서버 기동 → 수동 curl 검증 (사용자가 직접 수행)**

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

- [ ] **Step 4.3: Flutter 앱 회귀 확인 (사용자가 직접 수행)**

사용자에게 안내:
- 플러터 앱 재실행 → Todo 생성 → 더 이상 `_TypeError (type 'Null' is not a subtype of type 'String' in type cast)` 안 뜸
- 콘솔 로그에 `createdAt`/`updatedAt`이 ISO-8601 문자열로 찍히는지 확인

**4.2 / 4.3은 에이전트가 PASS로 표시하지 말고 사용자에게 확인을 받은 뒤 표시한다.**

- [ ] **Step 4.4: (선택) PR 본문 메모 준비**

PR을 생성한다면 본문에 한 줄 명시:
> Todo / TodoCategory 응답의 createdAt / updatedAt always-non-null 보장 — 서비스 레이어에서 `entityManager.flush()` 호출로 Hibernate가 응답 직전 timestamp를 채우도록 보장.

---

## 비범위 (Out of scope)

- `Persistable<ID>` 도입 (assigned-ID + merge → persist 전환으로 SELECT 1회 회피) — 본 결함과 분리된 코드 품질 이슈
- FuelTransaction / UserFuel (같은 assigned-ID 패턴이지만 응답 노출 여부 확인 후 별도 이슈)
- BaseTimeEntity 자체에 lifecycle 콜백 추가 (영향 범위 회피)
- `docs/api-docs.json` 재생성 (Springdoc 자동생성이면 다음 빌드/배포 사이클에서 처리)

---

## Self-Review

본 계획을 spec(`docs/superpowers/specs/2026-05-25-todo-timestamps-design.md`, revised)과 대조:

**Spec coverage:**
- spec §3.1 production 파일 2개 (TodoService, TodoCategoryService) ✅ Task 1, 2
- spec §3.1 service test 2개 ✅ Task 1, 2 (단위 테스트 verify flush)
- spec §3.1 repository test 2개 (선택) ✅ Task 3
- spec §3.2 service 변경 패턴 ✅ Task 1.3 / 2.3에 동일 코드
- spec §4 데이터 흐름 ✅ Task 1, 2 구현 코드와 일치
- spec §5.1 단위 테스트 패턴 ✅ Task 1.1 / 2.1
- spec §5.2 repository 회귀 테스트 ✅ Task 3
- spec §5.3 수동 curl ✅ Task 4.2
- spec §5.4 Flutter 회귀 ✅ Task 4.3
- spec §6 위험 검토 — Task 1.5 / 2.5 / 3.4 회귀 step으로 커버

**Placeholder scan:** TBD/TODO/"implement later" 등 없음. 모든 코드 블록에 실제 코드 포함.

**Type consistency:** `EntityManager.flush()` 호출 위치(`save()` 직후 또는 mutation 직후 / `log.info()` 직전)가 Task 1, 2에서 동일. Mock 패턴 동일. 회귀 테스트 패턴 Task 3에서 두 entity에 대칭 적용.

**의식적 비범위:** Controller 통합 테스트는 추가하지 않음 — `@Mock TodoService` stub 패턴이라 실제 timestamp 동작 검증 불가. 진짜 검증은 Service 단위 테스트(flush 호출) + Repository 통합 테스트(flush-time timestamp 동작) + 수동 curl이 담당.
