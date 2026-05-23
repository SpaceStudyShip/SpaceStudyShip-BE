# 할 일 + 카테고리 도메인 설계 (이슈 #24)

> **이슈**: [할일 및 카테고리 도메인 구현 #24](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/24)
> **브랜치**: `20260422_#24_할일_및_카테고리_도메인_구현`
> **버전**: version.yml `0.0.33` → `0.0.34`
> **마이그레이션**: `V0_0_34__add_todos_and_categories.sql`
> **API 스펙**: [docs/api-specs/02_todo.md](../../api-specs/02_todo.md)

---

## 1. 개요와 범위

API 스펙 8개 엔드포인트(Todo CRUD 4 + Category CRUD 4)를 구현한다. 동기화 전략은 **Tier 1 (Optimistic Updates)** — 클라이언트가 UUID를 생성해 보내고, 서버는 검증·영속화·소유권 확인만 담당한다.

### 범위 내
- Todo, TodoCategory Entity / Repository / Service
- TodoController, TodoCategoryController (SS-Web)
- Swagger 문서 (AuthController 수준 풀세트)
- ErrorCode 4개 추가 (Todo/Category × NotFound/AlreadyExists), 일관된 ErrorResponse 응답
- Flyway 마이그레이션 (`V0_0_34`) + version.yml bump
- 단위/통합 테스트 (Service / Repository / Controller)

### 범위 외
- Timer 도메인의 `actualMinutes` 누적 로직 (이슈 #26)
- Fuel/Exploration 연동
- 카테고리 맵 UI 좌표 검증의 비즈니스 의미 (스펙 그대로 0.0~1.0만 보장)

---

## 2. 모듈/패키지 구조

기존 빈 `SS-Study` 모듈에 `todo` 패키지로 배치. SS-Study는 향후 Timer까지 포함하는 "학습" 도메인 통합 모듈로 확장될 예정.

```text
SS-Study/src/main/java/com/elipair/spacestudyship/study/
└── todo/
    ├── dto/
    │   ├── TodoCreateRequest.java
    │   ├── TodoUpdateRequest.java
    │   ├── TodoResponse.java
    │   ├── CategoryCreateRequest.java
    │   ├── CategoryUpdateRequest.java
    │   └── CategoryResponse.java
    ├── entity/
    │   ├── Todo.java
    │   └── TodoCategory.java
    ├── repository/
    │   ├── TodoRepository.java
    │   └── TodoCategoryRepository.java
    └── service/
        ├── TodoService.java
        └── TodoCategoryService.java

SS-Web/src/main/java/com/elipair/spacestudyship/controller/todo/
├── TodoController.java
└── TodoCategoryController.java
```

기존 `study/{constant,dto,entity,repository,service}` 빈 폴더는 정리(또는 그대로 두고 todo 하위에서 시작). SS-Study `build.gradle`는 이미 `api project(':SS-Common')`, `api project(':SS-Member')` 포함 — 추가 의존성 불필요.

---

## 3. Entity 설계

### 3.1 Todo

```java
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
                              List<String> scheduledDates, List<String> categoryIds,
                              Integer estimatedMinutes) {
        return Todo.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .scheduledDates(scheduledDates == null ? List.of() : scheduledDates)
                .completedDates(List.of())
                .categoryIds(categoryIds == null ? List.of() : categoryIds)
                .estimatedMinutes(estimatedMinutes)
                .build();
    }

    public void updateTitle(String title) { this.title = title; }
    public void updateScheduledDates(List<String> dates) { this.scheduledDates = dates; }
    public void updateCompletedDates(List<String> dates) { this.completedDates = dates; }
    public void updateCategoryIds(List<String> ids) { this.categoryIds = ids; }
    public void updateEstimatedMinutes(Integer minutes) { this.estimatedMinutes = minutes; }
    public void updateActualMinutes(Integer minutes) { this.actualMinutes = minutes; }

    public void removeCategoryId(String categoryId) {
        this.categoryIds = this.categoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .toList();
    }
}
```

### 3.2 TodoCategory

```java
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

    public void updateName(String name) { this.name = name; }
    public void updateIconId(String iconId) { this.iconId = iconId; }
    public void updatePositionX(Double x) { this.positionX = x; }
    public void updatePositionY(Double y) { this.positionY = y; }
}
```

---

## 4. DTO 설계 (Record + `@Schema`)

### 4.1 Todo DTO

```java
@Schema(description = "할 일 생성 요청")
public record TodoCreateRequest(
        @Schema(description = "클라이언트 UUID v4 (없으면 서버 생성)", nullable = true,
                example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "제목 (1~100자)", example = "수학 문제 풀기")
        @NotBlank @Size(max = 100)
        String title,

        @Schema(description = "카테고리 ID 목록 (기본 [])", example = "[\"cat-uuid-1\"]")
        List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분, 1 이상)", nullable = true, example = "60")
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", example = "[\"2026-04-16\"]")
        List<String> scheduledDates
) {}

@Schema(description = "할 일 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record TodoUpdateRequest(
        @Schema(description = "제목 (1~100자)", nullable = true)
        @Size(min = 1, max = 100) String title,

        @Schema(description = "예정 날짜 목록", nullable = true)
        List<String> scheduledDates,

        @Schema(description = "완료 날짜 목록", nullable = true)
        List<String> completedDates,

        @Schema(description = "카테고리 ID 목록", nullable = true)
        List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true)
        @Min(1) Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true)
        @Min(0) Integer actualMinutes
) {}

@Schema(description = "할 일 응답")
public record TodoResponse(
        String id,
        String title,
        List<String> scheduledDates,
        List<String> completedDates,
        List<String> categoryIds,
        Integer estimatedMinutes,
        Integer actualMinutes,
        String createdAt,    // ISO 8601 UTC
        String updatedAt
) {
    public static TodoResponse from(Todo todo) { ... }
}
```

### 4.2 Category DTO

```java
@Schema(description = "카테고리 생성 요청")
public record CategoryCreateRequest(
        @Schema(description = "클라이언트 UUID (없으면 서버 생성)", nullable = true)
        String id,

        @Schema(description = "카테고리 이름 (1~20자)", example = "수학")
        @NotBlank @Size(max = 20)
        String name,

        @Schema(description = "아이콘 식별자", nullable = true, example = "math_icon")
        String iconId,

        @Schema(description = "맵 가로 위치 (0.0~1.0)", nullable = true, example = "0.3")
        @DecimalMin("0.0") @DecimalMax("1.0")
        Double positionX,

        @Schema(description = "맵 세로 위치 (0.0~1.0)", nullable = true, example = "0.5")
        @DecimalMin("0.0") @DecimalMax("1.0")
        Double positionY
) {}

@Schema(description = "카테고리 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record CategoryUpdateRequest(
        @Schema(nullable = true) @Size(min = 1, max = 20) String name,
        @Schema(nullable = true) String iconId,
        @Schema(nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") Double positionX,
        @Schema(nullable = true) @DecimalMin("0.0") @DecimalMax("1.0") Double positionY
) {}

@Schema(description = "카테고리 응답")
public record CategoryResponse(
        String id,
        String name,
        String iconId,
        Double positionX,
        Double positionY,
        String createdAt,
        String updatedAt
) {
    public static CategoryResponse from(TodoCategory category) { ... }
}
```

### 4.3 PATCH null vs 빈 배열 규약

| 클라이언트 입력 | 의미 |
|---------------|------|
| 필드 누락 / `null` | **변경 없음** |
| `[]` | **명시적으로 모두 제거** |
| 값 있는 배열 | **해당 값으로 교체** |

서비스 코드에서 `if (request.fieldX() != null) entity.updateFieldX(request.fieldX())` 패턴 사용.

---

## 5. Repository

```java
public interface TodoRepository extends JpaRepository<Todo, String> {
    List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND scheduled_dates @> CAST(:date AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndScheduledDate(@Param("userId") Long userId,
                                            @Param("date") String dateJsonLiteral);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND category_ids @> CAST(:categoryId AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndCategoryId(@Param("userId") Long userId,
                                         @Param("categoryId") String categoryIdJsonLiteral);

    boolean existsByIdAndUserId(String id, Long userId);
    Optional<Todo> findByIdAndUserId(String id, Long userId);
}
```

> `dateJsonLiteral`은 서비스에서 `"\"2026-04-16\""` 형태로 감싸서 전달 (jsonb `@>` 우변은 JSON 표현). 카테고리 ID도 동일.
>
> **안전성**: `date` / `categoryId`는 Controller에서 `@Pattern` 검증 후에만 서비스로 전달:
> - `date`: `@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")` (YYYY-MM-DD 강제)
> - `categoryId`: `@Pattern(regexp = "[a-zA-Z0-9-]+")` (UUID 문자 집합)
> 정규식 통과한 입력만 JSON literal로 조립하므로 따옴표 escape 우려 없음. `@Param` 바인딩으로 SQL injection은 prepared statement가 차단.

```java
public interface TodoCategoryRepository extends JpaRepository<TodoCategory, String> {
    List<TodoCategory> findByUserIdOrderByCreatedAtAsc(Long userId);
    boolean existsByIdAndUserId(String id, Long userId);
    Optional<TodoCategory> findByIdAndUserId(String id, Long userId);
    long countByIdInAndUserId(Collection<String> ids, Long userId);
}
```

---

## 6. Service 핵심 로직

### 6.1 TodoService

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoCategoryRepository categoryRepository;

    public List<TodoResponse> findAll(Long userId, String date, String categoryId) {
        List<Todo> todos;
        if (date != null && categoryId != null) {
            // 두 필터를 모두 만족하는 결과 (서비스에서 교집합)
            Set<String> byDate = todoRepository
                .findByUserIdAndScheduledDate(userId, "\"" + date + "\"")
                .stream().map(Todo::getId).collect(Collectors.toSet());
            todos = todoRepository
                .findByUserIdAndCategoryId(userId, "\"" + categoryId + "\"")
                .stream().filter(t -> byDate.contains(t.getId())).toList();
        } else if (date != null) {
            todos = todoRepository.findByUserIdAndScheduledDate(userId, "\"" + date + "\"");
        } else if (categoryId != null) {
            todos = todoRepository.findByUserIdAndCategoryId(userId, "\"" + categoryId + "\"");
        } else {
            todos = todoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return todos.stream().map(TodoResponse::from).toList();
    }

    @Transactional
    public TodoResponse create(Long userId, TodoCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (todoRepository.existsById(id)) {
            throw new CustomException(ErrorCode.TODO_ALREADY_EXISTS);
        }
        validateCategoryIds(userId, request.categoryIds());
        Todo todo = Todo.create(
            id, userId, request.title(),
            request.scheduledDates(), request.categoryIds(),
            request.estimatedMinutes());
        Todo saved = todoRepository.save(todo);
        log.info("[Todo] 생성 | userId={}, todoId={}", userId, saved.getId());
        return TodoResponse.from(saved);
    }

    @Transactional
    public TodoResponse update(Long userId, String todoId, TodoUpdateRequest request) {
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

        log.info("[Todo] 수정 | userId={}, todoId={}", userId, todoId);
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(Long userId, String todoId) {
        if (!todoRepository.existsByIdAndUserId(todoId, userId)) {
            throw new CustomException(ErrorCode.TODO_NOT_FOUND);
        }
        todoRepository.deleteById(todoId);
        log.info("[Todo] 삭제 | userId={}, todoId={}", userId, todoId);
    }

    private void validateCategoryIds(Long userId, List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return;
        long found = categoryRepository.countByIdInAndUserId(categoryIds, userId);
        if (found != categoryIds.stream().distinct().count()) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }
}
```

### 6.2 TodoCategoryService

```java
@Service
@RequiredArgsConstructor
@Slf4j
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
            id, userId, request.name(), request.iconId(),
            request.positionX(), request.positionY());
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

    @Transactional
    public void delete(Long userId, String categoryId) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // 연관 Todo의 categoryIds에서 제거
        List<Todo> affected = todoRepository
            .findByUserIdAndCategoryId(userId, "\"" + categoryId + "\"");
        affected.forEach(todo -> todo.removeCategoryId(categoryId));
        // dirty checking으로 자동 update

        categoryRepository.delete(category);
        log.info("[TodoCategory] 삭제 | userId={}, categoryId={}, affectedTodos={}",
            userId, categoryId, affected.size());
    }
}
```

---

## 7. Controller

`@AuthMember LoginMember loginMember` 패턴, 응답 코드는 스펙 그대로:

| 작업 | 응답 코드 |
|------|---------|
| 생성 | `201 Created` |
| 조회 | `200 OK` |
| 수정 | `200 OK` |
| 삭제 | `204 No Content` |

### 7.1 Swagger 어노테이션 정책 (AuthController 패턴)

각 컨트롤러에 `@Tag`, 각 메소드에 `@Operation` (summary + 상세 description) + `@ApiResponses`:
- **성공 케이스** (200/201/204): `@Schema(implementation = TodoResponse.class)` + `@ExampleObject`로 실제 JSON 본문 예시
- **에러 케이스** (400/401/404/409/500): `@Schema(implementation = ErrorResponse.class)` + `@ExampleObject`로 `{"code":"...", "message":"..."}` 예시

예시 (TodoController.create):
```java
@Operation(
    summary = "할 일 생성",
    description = """
        새 할 일을 생성합니다. 클라이언트가 UUID를 생성해 보내면 그대로 사용하고,
        생략 시 서버에서 UUID v4를 생성합니다.

        ### 동작
        1. id 충돌 검사 → 충돌 시 `409 TODO_ALREADY_EXISTS`
        2. categoryIds 실존 검증 → 누락 시 `404 CATEGORY_NOT_FOUND`
        3. 저장 후 생성된 객체 반환
        """)
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = TodoResponse.class),
            examples = @ExampleObject(name = "Created", value = "..."))),
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(name = "InvalidTitle",
                value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"title: 1자 이상 100자 이하여야 합니다.\"}"))),
    @ApiResponse(responseCode = "401", ...),
    @ApiResponse(responseCode = "404", description = "카테고리 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\", ...}"))),
    @ApiResponse(responseCode = "409", description = "동일 ID 중복",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(value = "{\"code\":\"TODO_ALREADY_EXISTS\", ...}"))),
    @ApiResponse(responseCode = "500", ...)
})
@PostMapping("/api/todos")
public ResponseEntity<TodoResponse> create(
        @AuthMember LoginMember loginMember,
        @RequestBody @Valid TodoCreateRequest request) {
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(todoService.create(loginMember.memberId(), request));
}
```

다른 7개 엔드포인트도 동일한 풀세트 적용.

### 7.2 모든 엔드포인트 매핑

| 메소드 | 경로 | 응답 | 주요 에러 |
|-------|-----|-----|---------|
| GET | `/api/todos` (`date`, `categoryId` 쿼리) | 200 | 401 |
| POST | `/api/todos` | 201 | 400, 401, 404, 409 |
| PATCH | `/api/todos/{todoId}` | 200 | 400, 401, 404 |
| DELETE | `/api/todos/{todoId}` | 204 | 401, 404 |
| GET | `/api/todo-categories` | 200 | 401 |
| POST | `/api/todo-categories` | 201 | 400, 401, 409 |
| PATCH | `/api/todo-categories/{categoryId}` | 200 | 400, 401, 404 |
| DELETE | `/api/todo-categories/{categoryId}` | 204 | 401, 404 |

> 다른 사용자의 리소스 접근은 **403이 아닌 404**로 통일 (정보 노출 방지). `findByIdAndUserId` 패턴.

---

## 8. ErrorCode 추가 (SS-Common)

비즈니스 NotFound / Conflict 4개만 추가. 입력값 형식 위반 (`INVALID_TITLE`, `INVALID_DATE_FORMAT`, `INVALID_CATEGORY_NAME` 등)은 `@Valid` 어노테이션 (`@NotBlank`, `@Size`, `@Min`, `@DecimalMin/Max`) 위반 시 기존 `GlobalExceptionHandler`가 자동으로 `INVALID_INPUT_VALUE`로 변환하여 응답한다 — 별도 ErrorCode 정의 불필요. 클라이언트는 `message` 본문으로 어떤 필드의 어떤 제약을 어겼는지 식별한다.

```java
// Todo
TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 할 일을 찾을 수 없습니다."),
TODO_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 할 일이 이미 존재합니다."),

// Todo Category
CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 카테고리를 찾을 수 없습니다."),
CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 카테고리가 이미 존재합니다."),
```

스펙(02_todo.md)에서 정의한 `INVALID_TITLE` 등의 코드명은 사용하지 않고 모두 `INVALID_INPUT_VALUE`로 응답한다. 클라이언트는 `code` 대신 HTTP 400 + `message`로 분기.

`GlobalExceptionHandler`가 이미 `CustomException` → `ErrorResponse{code, message}`로 변환하므로 응답 형식의 일관성은 자동 확보.

---

## 9. Flyway 마이그레이션

### 9.1 version.yml bump
`0.0.33` → `0.0.34` (이 작업의 시작 단계에서 변경)

### 9.2 V0_0_34__add_todos_and_categories.sql

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
    id                 VARCHAR(36) PRIMARY KEY,
    user_id            BIGINT      NOT NULL,
    title              VARCHAR(100) NOT NULL,
    scheduled_dates    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    completed_dates    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    category_ids       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    estimated_minutes  INTEGER,
    actual_minutes     INTEGER,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    CONSTRAINT fk_todos_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_todos_user ON todos(user_id);
```

> JSONB GIN 인덱스는 일단 생략 (Tier 1, 사용자별 Todo 수백 수준 → 풀스캔 무리없음). 데이터 증가 시 별도 마이그레이션으로 추가.

### 9.3 CLAUDE.md 마이그레이션 이력 표 업데이트

| 버전 | 파일 | 내용 |
|------|------|------|
| 0.0.31 | V0_0_31__add_user_devices.sql | 초기 스키마 |
| **0.0.34** | **V0_0_34__add_todos_and_categories.sql** | **todos, todo_categories 테이블 생성** |

---

## 10. 테스트 전략

### 10.1 SS-Study Repository 테스트 (Testcontainers PostgreSQL)

Spring Boot 4 슬라이스 부재 우회 — `StudyTestApplication` + `@ImportAutoConfiguration`:

```java
// SS-Study/src/test/java/.../study/StudyTestApplication.java
@SpringBootApplication
@EntityScan(basePackageClasses = {Todo.class, TodoCategory.class, /* Member, BaseTimeEntity */})
@EnableJpaRepositories(basePackageClasses = {TodoRepository.class, TodoCategoryRepository.class})
public class StudyTestApplication { }
```

```java
@SpringBootTest(classes = StudyTestApplication.class)
@Testcontainers
@ActiveProfiles("test")
class TodoRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // JSONB @> 쿼리 검증, scheduled_dates 필터, category_ids 필터, 교집합
}
```

### 10.2 Service 단위 테스트 (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {
    @Mock TodoRepository todoRepository;
    @Mock TodoCategoryRepository categoryRepository;
    @InjectMocks TodoService todoService;

    // 시나리오:
    // - create 성공 / id 충돌 → TODO_ALREADY_EXISTS
    // - create with invalid categoryIds → CATEGORY_NOT_FOUND
    // - update with PATCH null/빈 배열/값 → 각 필드 케이스
    // - delete 없는 todo → TODO_NOT_FOUND
    // - findAll 필터 조합 (date, categoryId, 둘 다)
}
```

`TodoCategoryServiceTest`도 동일 패턴. **delete 시 연관 Todo의 categoryIds 정리** 검증 케이스 필수.

### 10.3 Controller 테스트 (SS-Web, MockMvc)

기존 `AuthControllerTest` 패턴 따라 8개 엔드포인트의 happy path + 주요 에러 path. JWT 인증 mock은 기존 패턴 재사용.

---

## 11. 셀프 리뷰 체크리스트 (구현 시 확인)

- [ ] `CustomException(ErrorCode)` 던지기 — 직접 ResponseEntity 만들지 않기
- [ ] Service `@Transactional(readOnly = true)` + 쓰기 메소드만 `@Transactional`
- [ ] PATCH의 모든 분기에 null 가드 (`if (request.fieldX() != null)`)
- [ ] `findByIdAndUserId` 패턴 — 다른 사용자 리소스는 404
- [ ] categoryIds 실존 검증은 create / update 모두 적용
- [ ] Swagger 모든 엔드포인트에 200/201/204/400/401/404/409/500 응답 명시
- [ ] Query/Path 파라미터에 `@Pattern` 검증 (`date`, `categoryId`, `todoId`)
- [ ] 로그 포맷 `[도메인] 액션 | key=value` 컨벤션 준수
- [ ] 마이그레이션 파일에 민감한 값 없음
- [ ] version.yml bump 포함된 커밋

---

## 12. 작업 산출물 요약

| 분류 | 파일 |
|------|------|
| **Entity** | `study/todo/entity/Todo.java`, `study/todo/entity/TodoCategory.java` |
| **DTO** | `study/todo/dto/` — 6개 Record (Todo/Category × Create/Update/Response) |
| **Repository** | `study/todo/repository/TodoRepository.java`, `TodoCategoryRepository.java` |
| **Service** | `study/todo/service/TodoService.java`, `TodoCategoryService.java` |
| **Controller** | `controller/todo/TodoController.java`, `TodoCategoryController.java` |
| **ErrorCode** | `SS-Common/.../ErrorCode.java` (7개 추가) |
| **Migration** | `SS-Web/.../db/migration/V0_0_34__add_todos_and_categories.sql` |
| **version.yml** | `0.0.33` → `0.0.34` |
| **CLAUDE.md** | 마이그레이션 이력 표에 V0_0_34 추가 |
| **Test (SS-Study)** | `StudyTestApplication`, `TodoRepositoryTest`, `TodoCategoryRepositoryTest`, `TodoServiceTest`, `TodoCategoryServiceTest` |
| **Test (SS-Web)** | `TodoControllerTest`, `TodoCategoryControllerTest` |
