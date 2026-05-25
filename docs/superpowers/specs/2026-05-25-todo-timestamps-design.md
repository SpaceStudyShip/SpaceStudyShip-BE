# Todo / TodoCategory 응답의 createdAt·updatedAt null 반환 수정 — 설계

- **작성일:** 2026-05-25
- **트리거 이슈:** 프론트엔드(Flutter) `_TypeError: type 'Null' is not a subtype of type 'String' in type cast` — Todo 생성 직후 응답의 `createdAt`/`updatedAt`이 null로 내려와 클라이언트 파서가 죽음
- **수정 범위:** Todo, TodoCategory (assigned-ID 패턴 + BaseTimeEntity 조합 도메인 중 응답에 시간 필드를 노출하는 것)
- **수정 방식:** `Persistable<String>` 인터페이스 구현 + update 경로에서 `EntityManager.flush()` 보장

---

## 1. 배경 및 진단

### 1.1 현상

```
POST /api/todos
→ 200 OK
{ "id": "...", "title": "...", ..., "createdAt": null, "updatedAt": null }
```

OpenAPI 스펙(`docs/api-docs.json`의 `TodoResponse`)은 `createdAt`/`updatedAt`을 `string`(non-null)로 정의 → 구현이 명세를 위반.

### 1.2 진단 결과 — 작업 지시서의 가설은 모두 틀림

| 가설 | 실제 |
|------|------|
| JPA Auditing 미활성화 | `BaseTimeEntity`는 Hibernate `@CreationTimestamp`/`@UpdateTimestamp` 사용 (`@EnableJpaAuditing` 불필요) |
| DTO 매핑 누락 | `TodoResponse.from()`이 `todo.getCreatedAt()` 호출 — 정상. 단, `formatUtc()`가 null이면 null 반환 |
| save 대신 INSERT | 정상적으로 `repository.save()` 사용 |

### 1.3 진짜 원인 — assigned-ID + Spring Data JPA save() 동작

`Todo` / `TodoCategory`는 **클라이언트가 UUID를 직접 부여**하는 entity (`@Id @Column(length=36) private String id;`). 반면 정상 동작하는 `UserDevice` 등은 `@GeneratedValue(IDENTITY)`.

Spring Data JPA `SimpleJpaRepository.save()`는 ID 존재 여부로 새 엔티티를 판단:

| 케이스 | save() 동작 | 결과 |
|--------|-----------|------|
| IDENTITY + ID null | `entityManager.persist()` 호출 | INSERT 즉시 + `@CreationTimestamp` PrePersist에서 채워짐 |
| **assigned ID (Todo)** | **`merge()` 호출** | merge는 detached entity의 managed copy 반환만 함, INSERT는 트랜잭션 flush 시점에 지연 → save() 반환 시점에 `getCreatedAt()`이 **null** |

응답은 `save()` 반환값을 바로 `TodoResponse.from(saved)`에 넘기므로, 변환 시점에 entity의 시간 필드가 null → `formatUtc(null) → null` → JSON에 `"createdAt": null` 직렬화.

### 1.4 update() 경로의 잠재 결함 (작업 지시서 미언급)

`update()`도 같은 결함이 있음:

- `findByIdAndUserId`로 managed entity 가져옴 (이미 createdAt/updatedAt 채워져 있음 → null은 아님)
- 필드 mutation 후 dirty checking → flush 시점에 UPDATE + `@UpdateTimestamp` 갱신
- 그러나 `TodoResponse.from(todo)` 호출 시점이 flush 전이면 → **응답의 updatedAt이 변경 전 값(stale)** 으로 내려감
- 작업 지시서 6.2 검증 시나리오("updatedAt이 갱신되어야 함") 위반

### 1.5 영향 범위

| Entity | ID 전략 | 결함 발생? | 응답에 시간 노출? | 본 설계 수정 대상? |
|--------|---------|-----------|------------------|-------------------|
| Todo | assigned String | 예 | 예 | **예** |
| TodoCategory | assigned String | 예 | 예 (`CategoryResponse`) | **예** |
| FuelTransaction | assigned String | 잠재 | 별도 확인 필요 | 아니오 (범위 외) |
| UserFuel | assigned Long(userId) | 잠재 | 별도 확인 필요 | 아니오 (범위 외) |
| UserDevice / Member | IDENTITY | 없음 | — | — |

FuelTransaction/UserFuel은 응답 DTO가 시간 필드를 노출하지 않으면 사용자에게 보이지 않으므로 본 설계에서는 제외. 별도 후속 이슈로 점검.

---

## 2. 결정 사항

### 2.1 해결 방식: `Persistable<ID>` 구현 (정석)

대안 비교:

| 옵션 | 채택? | 사유 |
|------|-------|------|
| **Persistable<String> 구현** | ✅ | 정석. Spring Data가 `persist()` 호출 → `@CreationTimestamp`가 PrePersist에서 즉시 채워짐. 보일러플레이트 최소. |
| BaseTimeEntity에 Persistable 통합 | ❌ | UserDevice(Long) / Todo(String)가 섞여 generic 시그니처 복잡. 영향 범위 확대 위험. |
| saveAndFlush() 사용 | ❌ | merge 호출 자체는 그대로 → 불필요한 SELECT. 영속성 컨텍스트 전체 flush 부작용. |
| EntityManager.persist() 직접 호출 | ❌ | Spring Data 추상화 깨고 모든 service에 EntityManager 주입 강제. |

### 2.2 수정 범위: Todo + TodoCategory만

작업 지시서가 명시한 범위. Fuel 도메인은 응답 노출 여부 별도 확인 후 후속 이슈로 처리.

### 2.3 update()도 함께 보장

create() 뿐 아니라 update() 응답의 updatedAt도 항상 최신 값이 내려가도록 `EntityManager.flush()`로 강제.

---

## 3. 변경 상세

### 3.1 변경 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/Todo.java` | `implements Persistable<String>` 추가, `getId()`·`isNew()` 메서드 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/entity/TodoCategory.java` | 동일 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java` | `EntityManager` 주입, `update()`에 `entityManager.flush()` 호출 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryService.java` | 동일 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java` | create/update 응답의 시간 필드 검증 추가 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoCategoryServiceTest.java` | 동일 |
| `SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoControllerTest.java` | 응답 JSON에 `$.createdAt`/`$.updatedAt` not-null 검증 |
| `SS-Web/src/test/java/com/elipair/spacestudyship/controller/todo/TodoCategoryControllerTest.java` | 동일 |

**미변경:**
- `BaseTimeEntity` — IDENTITY 기반 엔티티에 영향 주지 않기 위해 그대로 둠
- DB 스키마 / Flyway 마이그레이션 — 스키마 변경 없음
- `TodoResponse` / `CategoryResponse` — 변환 로직 정상. 입력으로 들어오는 entity 시간 필드가 채워지기만 하면 됨
- `version.yml` — CI가 patch 자동 증가 처리 (수동 수정 없음)

### 3.2 엔티티 변경 패턴 (Todo / TodoCategory 동일)

```java
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "todos")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(length = 36)
    private String id;

    // 기존 필드/메서드 유지

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
```

**판단 기준의 의미:** 영속화 전이면 `@CreationTimestamp`가 아직 채워지지 않아 createdAt이 null → `isNew()=true` → Spring Data가 `persist()` 호출. 영속화 후 detach → reattach 시나리오에서는 createdAt이 있으므로 `isNew()=false` → `merge()` 호출. 정석 패턴.

### 3.3 서비스 변경 패턴 (TodoService / TodoCategoryService 동일)

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
        // 기존 코드 변경 없음 — Persistable 적용만으로 해결됨
        // ...
        Todo saved = todoRepository.save(todo);
        return TodoResponse.from(saved);
    }

    @Transactional
    public TodoResponse update(Long userId, String todoId, TodoUpdateRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        // 기존 mutation 코드 유지
        if (request.title() != null) todo.updateTitle(request.title());
        // ...

        entityManager.flush();  // ← 추가: dirty checking → UPDATE → @UpdateTimestamp 갱신
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
      ├─ Todo.create(...)            // 새 entity, getCreatedAt() == null
      ├─ todoRepository.save(todo)
      │  └─ SimpleJpaRepository.save()
      │     └─ isNew() = true → entityManager.persist(todo)
      │        └─ Hibernate: PrePersist 콜백 → @CreationTimestamp/@UpdateTimestamp 채움
      │           (INSERT SQL은 트랜잭션 commit 시 실행, entity 객체엔 값 들어감)
      └─ TodoResponse.from(saved)    // saved.getCreatedAt() != null ✅
```

### 4.2 update

```
PATCH /api/todos/{id}
└─ TodoController.update()
   └─ TodoService.update() [@Transactional]
      ├─ findByIdAndUserId(...)      // managed entity, 기존 createdAt/updatedAt 보유
      ├─ todo.updateXxx(...)         // 필드 mutation (dirty)
      ├─ entityManager.flush()       // ← UPDATE 실행 + @UpdateTimestamp 갱신
      └─ TodoResponse.from(todo)     // 갱신된 updatedAt ✅
```

---

## 5. 테스트 전략

### 5.1 Service 단위 테스트

**TodoServiceTest.create()** (기존 테스트 보강):
```java
TodoResponse res = todoService.create(userId, request);
assertThat(res.createdAt()).isNotNull();
assertThat(res.updatedAt()).isNotNull();
// ISO-8601 UTC 패턴 검증
assertThat(res.createdAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
```

**TodoServiceTest.update()** (신규):
```java
TodoResponse created = todoService.create(userId, request);
// clock skew 회피: 짧은 대기 또는 mock clock 사용
TodoResponse updated = todoService.update(userId, created.id(), updateRequest);
assertThat(updated.createdAt()).isEqualTo(created.createdAt());
assertThat(updated.updatedAt()).isNotNull();
// Hibernate LocalDateTime은 microsecond 단위 — >= 으로 비교
assertThat(parseIso(updated.updatedAt()))
        .isAfterOrEqualTo(parseIso(created.updatedAt()));
```

**TodoCategoryServiceTest** 동일 패턴.

### 5.2 Controller 통합 테스트

```java
mockMvc.perform(post("/api/todos").contentType(JSON).content(...))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.createdAt").isNotEmpty())
   .andExpect(jsonPath("$.updatedAt").isNotEmpty())
   .andExpect(jsonPath("$.createdAt")
       .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")));
```

기존 메모리 `Spring Boot 4 test slice` 패턴 (TestApplication + `@ImportAutoConfiguration`) 그대로 적용.

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

### 6.1 Persistable 도입 후 회귀 위험

- **같은 ID로 두 번 create() 호출:** 기존 코드는 `existsById()` 사전 체크로 `TODO_ALREADY_EXISTS` 예외. Persistable 적용 후엔 만약 사전 체크를 우회하더라도 `persist()`가 `EntityExistsException` 발생. **정상 경로에서는 동작 동일**.
- **`@AllArgsConstructor`와의 충돌:** Persistable의 `getId()`는 인터페이스 메서드, 필드 추가 아님. Lombok 생성자에 영향 없음.

### 6.2 EntityManager.flush() 추가 위험

- **부분 commit 불가:** flush는 commit이 아니므로 트랜잭션 롤백 가능성 유지.
- **다른 dirty entity도 같이 flush:** update() 내에서 다른 entity 변경이 없으므로 영향 없음. 정책상 update는 단일 entity 갱신만 수행.

### 6.3 OpenAPI 스펙 영향

`docs/api-docs.json` 자동 생성이면 entity 변경 후 재생성. `TodoResponse`/`CategoryResponse` 스키마 자체는 변경 없음 (필드 그대로) → 클라이언트 호환성 영향 없음.

### 6.4 다른 도메인의 잠재 결함

FuelTransaction/UserFuel은 동일 패턴이지만 응답 노출 여부 확인 후 별도 이슈로 처리. 본 설계에서는 다루지 않음.

---

## 7. 작업 순서

1. **Todo / TodoCategory 엔티티**에 `Persistable<String>` 구현 추가
2. **TodoService / TodoCategoryService update()** 에 `entityManager.flush()` 추가
3. **Service 단위 테스트** 보강 (create/update의 createdAt/updatedAt 검증)
4. **Controller 통합 테스트** 보강 (응답 JSON 검증)
5. `./gradlew :SS-Study:test :SS-Web:test` 전체 통과 확인
6. `./gradlew :SS-Web:bootRun` 후 §5.3 curl 시나리오 수동 검증
7. Flutter 앱 회귀 확인 (§5.4)
8. PR 본문에 "Todo/TodoCategory 응답의 createdAt/updatedAt always-non-null 보장" 명시

---

## 8. 비범위 (Out of scope)

- Fuel 도메인 (UserFuel, FuelTransaction) 동일 패턴 점검 — 별도 후속 이슈
- BaseTimeEntity 통합 리팩토링 — 영향 범위 확대 위험으로 보류
- OpenAPI 스펙 nullable=false 명시 — 이미 그렇게 되어 있음 (구현이 명세를 어기는 상황이었음)
- `docs/api-docs.json` 재생성 자동화 — Springdoc 동작 검증 후 별도 처리
