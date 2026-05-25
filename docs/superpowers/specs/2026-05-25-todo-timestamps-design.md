# Todo / TodoCategory 응답의 createdAt·updatedAt null 반환 수정 — 설계

- **작성일:** 2026-05-25 (revised after implementer diagnostic)
- **트리거 이슈:** 프론트엔드(Flutter) `_TypeError: type 'Null' is not a subtype of type 'String' in type cast` — Todo 생성 직후 응답의 `createdAt`/`updatedAt`이 null로 내려와 클라이언트 파서가 죽음
- **수정 범위:** Todo, TodoCategory 서비스 레이어 (entity 변경 없음)
- **수정 방식:** `TodoService`/`TodoCategoryService`의 create / update 경로에 `EntityManager.flush()` 호출 추가 → 응답 직전에 INSERT/UPDATE 강제 실행 → Hibernate `@CreationTimestamp`/`@UpdateTimestamp` 채워짐

---

## 1. 배경 및 진단

### 1.1 현상

```
POST /api/todos
→ 200 OK
{ "id": "...", "title": "...", ..., "createdAt": null, "updatedAt": null }
```

OpenAPI 스펙(`docs/api-docs.json`의 `TodoResponse`)은 `createdAt`/`updatedAt`을 `string`(non-null)로 정의 → 구현이 명세를 위반.

### 1.2 잘못 짚었던 원인들 (작업 지시서 + 초기 설계 모두 틀림)

| 가설 | 실제 |
|------|------|
| JPA Auditing 미활성화 | `BaseTimeEntity`는 Hibernate `@CreationTimestamp`/`@UpdateTimestamp` 사용 (`@EnableJpaAuditing` 불필요) |
| DTO 매핑 누락 | `TodoResponse.from()`이 `todo.getCreatedAt()` 호출 — 정상. 단, `formatUtc()`가 null이면 null 반환 |
| save 대신 INSERT | 정상적으로 `repository.save()` 사용 |
| **assigned-ID + Persistable 미구현이 핵심 → persist 호출로 즉시 timestamp 채워짐** | **틀림** — Hibernate 7의 `@CreationTimestamp`는 `CurrentTimestampGeneration` (BeforeExecutionGenerator, source=VM)으로 구현되어 **flush 시점에** `EntityInsertAction` 실행 중 fire. `persist()` 호출 시점 아님. Persistable은 timestamp에 영향 없음 |

### 1.3 진짜 원인 — Hibernate `@CreationTimestamp`의 flush-time 동작

`Todo` / `TodoCategory`의 `save()` 후 응답 시점에 entity의 `createdAt`/`updatedAt`이 null인 이유:

1. `TodoService.create()`는 `@Transactional` 안에서 `repository.save(todo)` 호출 → 반환된 entity를 `TodoResponse.from(saved)`로 즉시 직렬화
2. Hibernate 7의 `@CreationTimestamp`는 `BeforeExecutionGenerator(source=VM)`로 등록되어 있어, **flush 단계의 `EntityInsertAction.execute()` 안에서** `LocalDateTime.now()`를 entity 필드에 set
3. `save()` 자체는 영속화 컨텍스트에 등록만 하고 INSERT SQL은 실행하지 않음 (auto-flush가 일어나지 않는 한 트랜잭션 commit 전까지 지연)
4. 응답 직렬화 시점 ≤ flush 시점이면 → `entity.getCreatedAt() == null` → `TodoResponse`의 `formatUtc(null) → null` → JSON에 `"createdAt": null`

### 1.4 보조 사실 — assigned-ID 패턴

`Todo`/`TodoCategory`는 클라이언트가 UUID를 직접 부여 (`@Id @Column(length=36) private String id`). 정상 동작하는 `UserDevice` 등은 `@GeneratedValue(IDENTITY)`.

- IDENTITY: ID 생성을 위해 Hibernate가 즉시 INSERT 실행 → flush 즉시 발생 → `@CreationTimestamp` 즉시 채워짐 → 응답 시점 안전
- **assigned ID**: ID가 이미 있어 즉시 INSERT 불필요 → flush까지 INSERT 지연 → 응답 시점에 null

즉 assigned-ID는 `@GeneratedValue(IDENTITY)` 케이스가 운 좋게 "공짜로" 받던 즉시-flush 효과가 없음.

### 1.5 update() 경로의 동일 결함

update도 같은 메커니즘:
- `findByIdAndUserId`로 가져온 managed entity는 기존 timestamp 보유 (DB row에 NOT NULL로 있음 → null은 아님)
- 필드 mutation 후 dirty checking → flush 시점에 UPDATE + `@UpdateTimestamp` 갱신
- 응답을 flush 전에 만들면 → `updatedAt`이 **변경 전 값(stale)** 으로 내려감
- 작업 지시서 6.2 검증("updatedAt이 갱신되어야 함") 위반

### 1.6 영향 범위

| Entity | ID 전략 | 결함 발생? | 응답에 시간 노출? | 본 설계 수정 대상? |
|--------|---------|-----------|------------------|-------------------|
| Todo | assigned String | 예 | 예 | **예** |
| TodoCategory | assigned String | 예 | 예 (`CategoryResponse`) | **예** |
| FuelTransaction | assigned String | 잠재 | 별도 확인 필요 | 아니오 (범위 외) |
| UserFuel | assigned Long(userId) | 잠재 | 별도 확인 필요 | 아니오 (범위 외) |
| UserDevice / Member | IDENTITY | 없음 | — | — |

Fuel 도메인은 응답 노출 여부 별도 점검 후 후속 이슈로 처리.

---

## 2. 결정 사항

### 2.1 해결 방식: 서비스 레이어에서 `EntityManager.flush()` 호출

대안 비교:

| 옵션 | 채택? | 사유 |
|------|-------|------|
| **서비스에 EntityManager 주입 + create/update에 flush() 호출** | ✅ | 가장 단순. timestamp 동작이 명시적. entity / BaseTimeEntity 변경 없음. 영향 범위 최소. |
| `repository.saveAndFlush()` 사용 | ❌ | create에는 적합하나 update는 dirty entity를 `save()` 호출하는 게 어색 (이미 managed). 두 경로 일관성 떨어짐. |
| `Persistable<String>` 구현 | ❌ | timestamp 문제와 무관 (Hibernate가 flush 시점에 채우므로). merge → persist 전환으로 SELECT 1회 회피하는 부가 효과는 있으나 본 결함과 분리된 이슈 |
| `BaseTimeEntity`에 `@PrePersist/@PreUpdate` 콜백 추가 (수동 시간 설정) | ❌ | 동작은 하지만 `@CreationTimestamp`/`@UpdateTimestamp`와 중복. BaseTimeEntity가 모든 자식 entity에 영향 → 회귀 위험. |
| 응답 acceptance 변경 (null 허용) | ❌ | 클라이언트가 non-null 가정. OpenAPI 스펙 위반. |

### 2.2 수정 범위: Todo + TodoCategory 서비스만

작업 지시서가 명시한 범위. Fuel 도메인은 응답 노출 여부 별도 확인 후 후속 이슈로 처리.

### 2.3 create / update 둘 다 보장

- create: 응답의 createdAt/updatedAt이 항상 채워지도록
- update: 응답의 updatedAt이 갱신 반영되도록

두 경로 모두 mutation 직후 `entityManager.flush()` 호출.

---

## 3. 변경 상세

### 3.1 변경 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java` | `EntityManager` 주입, `create()` save 직후 / `update()` mutation 직후에 `entityManager.flush()` 호출 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java` | 동일 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java` | EntityManager mock 추가 + create/update에서 flush() 호출 verify |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java` | 동일 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java` | (선택) `saveAndFlush` 후 createdAt not null 회귀 테스트 추가 — Hibernate의 flush-time timestamp 동작이 깨지면 알람 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoCategoryRepositoryTest.java` | 동일 (선택) |

**미변경:**
- `Todo`, `TodoCategory` entity — 그대로
- `BaseTimeEntity` — 그대로
- `TodoResponse`, `CategoryResponse` — 그대로
- DB 스키마 / Flyway 마이그레이션 — 그대로
- `version.yml` — CI가 patch 자동 증가 처리

### 3.2 서비스 변경 패턴 (TodoService / TodoCategoryService 동일)

```java
import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoCategoryRepository categoryRepository;
    private final EntityManager entityManager;  // ← 추가

    @Transactional
    public TodoResponse create(Long userId, TodoCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (todoRepository.existsById(id)) {
            throw new CustomException(ErrorCode.TODO_ALREADY_EXISTS);
        }
        validateCategoryIds(userId, request.categoryIds());

        Todo todo = Todo.create(...);
        Todo saved = todoRepository.save(todo);
        entityManager.flush();  // ← 추가: INSERT 실행 + @CreationTimestamp 채워짐
        log.info("[Todo] 생성 | userId={}, todoId={}", userId, saved.getId());
        return TodoResponse.from(saved);
    }

    @Transactional
    public TodoResponse update(Long userId, String todoId, TodoUpdateRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        // 기존 mutation 코드 유지
        if (request.title() != null) todo.updateTitle(request.title());
        // ...

        entityManager.flush();  // ← 추가: UPDATE 실행 + @UpdateTimestamp 갱신
        log.info("[Todo] 수정 | userId={}, todoId={}", userId, todoId);
        return TodoResponse.from(todo);
    }
}
```

`delete()`는 응답이 204 (body 없음)이므로 변경 불필요.

---

## 4. 데이터 흐름 (수정 후)

### 4.1 create

```
POST /api/todos
└─ TodoController.create()
   └─ TodoService.create() [@Transactional]
      ├─ Todo.create(...)            // 새 entity, createdAt == null
      ├─ todoRepository.save(todo)   // 영속화 컨텍스트 등록 (INSERT 아직 안 실행)
      ├─ entityManager.flush()       // ← INSERT SQL 실행
      │  └─ Hibernate: EntityInsertAction → CurrentTimestampGeneration
      │     → entity.createdAt = LocalDateTime.now()
      │     → entity.updatedAt = LocalDateTime.now()
      └─ TodoResponse.from(saved)    // saved.getCreatedAt() != null ✅
```

### 4.2 update

```
PATCH /api/todos/{id}
└─ TodoController.update()
   └─ TodoService.update() [@Transactional]
      ├─ findByIdAndUserId(...)      // managed entity, 기존 timestamp 보유
      ├─ todo.updateXxx(...)         // 필드 mutation (dirty)
      ├─ entityManager.flush()       // ← UPDATE SQL 실행
      │  └─ Hibernate: EntityUpdateAction → CurrentTimestampGeneration
      │     → entity.updatedAt = LocalDateTime.now()
      └─ TodoResponse.from(todo)     // 갱신된 updatedAt ✅
```

---

## 5. 테스트 전략

### 5.1 Service 단위 테스트 (Mockito)

`@Mock EntityManager entityManager;` 필드 추가. 다음 테스트 신규:

**create 경로:**
```java
@Test
@DisplayName("create: save 후 EntityManager.flush() 호출 — createdAt/updatedAt 보장")
void create_flushesAfterSave() {
    var request = new TodoCreateRequest("t1", "수학", List.of(), null, List.of());
    when(todoRepository.existsById("t1")).thenReturn(false);
    when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

    todoService.create(1L, request);

    verify(entityManager).flush();
}
```

**update 경로:**
```java
@Test
@DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 갱신 보장")
void update_flushesAfterMutation() {
    Todo existing = Todo.create("t1", 1L, "원본", null, null, null);
    when(todoRepository.findByIdAndUserId("t1", 1L)).thenReturn(Optional.of(existing));

    var request = new TodoUpdateRequest("새 제목", null, null, null, null, null);
    todoService.update(1L, "t1", request);

    verify(entityManager).flush();
}
```

기존 단위 테스트들도 `EntityManager` mock 추가로 동작에 영향 없는지 확인 (mock된 flush는 no-op).

`TodoCategoryServiceTest` 동일 패턴.

### 5.2 Repository 통합 테스트 (선택, 회귀 알람용)

`TodoRepositoryTest` / `TodoCategoryRepositoryTest`에 회귀 테스트 추가:

```java
@Test
@DisplayName("saveAndFlush: assigned-ID Todo도 flush 후 timestamp 채워짐 (Hibernate 회귀 알람)")
void saveAndFlush_populatesTimestamps() {
    Todo saved = todoRepository.saveAndFlush(Todo.create("t-ts", 1L, "X", null, null, null));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
}
```

이 테스트는 Hibernate의 flush-time `@CreationTimestamp` 동작이 미래 버전 업그레이드 등으로 깨지면 즉시 알람.

### 5.3 수동 검증 (작업 지시서 §6.2 시나리오)

```bash
TOKEN="..."

# 생성
curl -X POST http://localhost:8080/api/todos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"id":"11111111-1111-1111-1111-111111111111","title":"테스트","scheduledDates":["2026-05-25"]}' \
  | jq '.createdAt, .updatedAt'
# 기대: 둘 다 ISO-8601 UTC 문자열

# 수정
curl -X PATCH http://localhost:8080/api/todos/11111111-1111-1111-1111-111111111111 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"수정"}' \
  | jq '.createdAt, .updatedAt'
# 기대: createdAt 동일, updatedAt 갱신
```

### 5.4 Flutter 앱 회귀 확인

작업 지시서 §6.3:
- 백엔드 수정 후 Flutter 앱 재실행
- Todo 생성 시 `_TypeError` 미발생
- 콘솔 로그에 ISO-8601 문자열 확인

---

## 6. 위험 / 회귀 검토

### 6.1 `entityManager.flush()` 호출 추가 위험

- **부분 commit 불가:** flush는 commit이 아니므로 트랜잭션 롤백 가능성 유지. flush 후 예외가 발생하면 INSERT/UPDATE도 롤백됨.
- **다른 dirty entity 같이 flush:** create()/update() 내에서 다른 entity 변경이 없으므로 영향 없음. 정책상 메서드는 단일 entity 갱신만 수행.
- **성능:** 트랜잭션당 추가 SQL round-trip 1회. create/update는 원래 INSERT/UPDATE가 commit 시점에 실행되던 것이 단지 조금 앞당겨질 뿐. 측정 가능한 성능 영향 없음.

### 6.2 OpenAPI 스펙 영향

`docs/api-docs.json` 자동 생성이면 entity 변경 없으므로 재생성 결과 동일. 클라이언트 호환성 영향 없음.

### 6.3 다른 도메인의 잠재 결함

FuelTransaction/UserFuel은 동일 패턴이지만 응답 노출 여부 확인 후 별도 이슈로 처리. 본 설계에서는 다루지 않음.

### 6.4 Hibernate 버전 종속

`@CreationTimestamp`의 flush-time 동작은 Hibernate 7의 `CurrentTimestampGeneration` 구현 세부. 향후 메이저 업그레이드 시 동작이 다시 바뀔 수 있음 — §5.2 Repository 회귀 테스트로 알람 보장.

---

## 7. 작업 순서

1. **TodoService**에 EntityManager 주입 + `create()` / `update()`에 `entityManager.flush()` 추가
2. **TodoServiceTest**에 flush() 호출 verify 테스트 추가 (create/update 각각)
3. **TodoCategoryService** 동일 변경
4. **TodoCategoryServiceTest** 동일 변경
5. **TodoRepositoryTest / TodoCategoryRepositoryTest**에 saveAndFlush 회귀 테스트 추가 (선택)
6. `./gradlew :SS-Study:test :SS-Web:test` 전체 통과 확인
7. `./gradlew :SS-Web:bootRun` 후 §5.3 curl 시나리오 수동 검증
8. Flutter 앱 회귀 확인 (§5.4)
9. PR 본문에 "Todo/TodoCategory 응답의 createdAt/updatedAt always-non-null 보장 (서비스 레이어에서 flush 호출)" 명시

---

## 8. 비범위 (Out of scope)

- Fuel 도메인 (UserFuel, FuelTransaction) 동일 패턴 점검 — 별도 후속 이슈
- `Persistable<ID>` 도입 (assigned-ID + merge → persist 전환으로 SELECT 1회 회피) — 본 결함과 분리된 코드 품질 이슈로 후속 처리 가능
- BaseTimeEntity 리팩토링 — 영향 범위 확대 회피
- OpenAPI 스펙 nullable=false 명시 — 이미 그렇게 되어 있음
- `docs/api-docs.json` 재생성 자동화

---

## 9. 변경 이력

- **2026-05-25 (초안):** assigned-ID + Persistable 구현으로 해결 가능하다고 잘못 진단 → Persistable이 `persist()` 호출을 보장하지만 `@CreationTimestamp`는 flush 시점 동작이므로 entity의 timestamp는 여전히 null. Implementer subagent가 검증 후 BLOCKED 보고로 발견.
- **2026-05-25 (수정):** 진단 정정 — Hibernate 7의 `@CreationTimestamp`는 `CurrentTimestampGeneration(BeforeExecutionGenerator, source=VM)`로 flush 시점 동작. 해결 방식을 서비스 레이어 `entityManager.flush()` 호출로 단순화. Persistable / BaseTimeEntity 수정은 비범위로 이동.
