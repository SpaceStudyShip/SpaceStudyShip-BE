# 타이머 세션 도메인 설계 (이슈 #25)

> **이슈**: [⚙️[기능추가][타이머] 타이머 세션 도메인 구현 #25](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/25)
> **브랜치**: `20260422_#25_타이머_세션_도메인_구현`
> **버전**: version.yml `0.0.38` → `0.0.39`
> **마이그레이션**: `V0_0_39__add_timer_sessions.sql`
> **API 스펙**: [docs/api-specs/03_timer.md](../../api-specs/03_timer.md)
> **연관 도메인**: Fuel (#26 완료), Todo (#24 완료)

---

## 1. 개요와 범위

API 스펙의 3개 엔드포인트(`POST/GET /api/timer-sessions`, `GET /api/timer-sessions/today-stats`)를 구현한다. 동기화 전략은 **Tier 2 (Server-Validated)** — 시간 유효성을 서버가 검증하고, 검증 통과 시 같은 트랜잭션에서 연료 충전과 Todo `actualMinutes` 누적까지 처리한 뒤 확정값을 반환한다.

### 범위 내
- `TimerSession` Entity / Repository / Service (SS-Study `study/timer/` 패키지)
- `TimerSessionController` (SS-Web)
- 3개 엔드포인트
  - `POST /api/timer-sessions` — 세션 저장 + 시간 검증 + Fuel 충전 + Todo actualMinutes 누적 (단일 트랜잭션)
  - `GET /api/timer-sessions` — 날짜 범위/todoId 필터 + 페이지네이션
  - `GET /api/timer-sessions/today-stats` — 오늘 총 분/세션 수/연속 일수(streak), KST 기준
- 헤더 `Idempotency-Key` 옵션 지원 — 모바일 재시도 안전성 확보
- `TodoService.addActualMinutes()` 신규 메서드 — atomic UPDATE로 누적 (lost update 방지)
- ErrorCode 5개 추가 (`INVALID_SESSION_TIME`, `INVALID_DURATION`, `SESSION_TOO_SHORT`, `SESSION_TOO_LONG`, `FUTURE_SESSION`)
- Flyway 마이그레이션 + version.yml bump
- Swagger 풀세트 (Todo/Fuel 컨트롤러 수준)
- 단위/통합 테스트

### 범위 외
- 세션 수정/삭제 API (스펙에 없음)
- 일시정지/재개 등 진행 중 상태의 서버 추적 (클라이언트 책임)
- streak 사전 계산 캐시 (YAGNI — 매 호출 distinct 쿼리, 최근 365일 상한)
- 글로벌 timezone 지원 (KST 고정, 상수 1곳에 집중)
- Fuel 충전량 보너스 정책 (1분 = 1연료 단순 환율 유지)

---

## 2. 모듈/패키지 구조

기존 `SS-Study` 모듈에 `timer/` 패키지로 배치. Fuel 도메인과 동형 구조.

```text
SS-Study/src/main/java/com/elipair/spacestudyship/study/
└── timer/
    ├── dto/
    │   ├── TimerSessionCreateRequest.java
    │   ├── TimerSessionCreateResponse.java     ← { session, fuelCharged }
    │   ├── TimerSessionResponse.java
    │   ├── TimerSessionListResponse.java
    │   └── TodayStatsResponse.java
    ├── entity/
    │   └── TimerSession.java
    ├── repository/
    │   └── TimerSessionRepository.java
    └── service/
        └── TimerSessionService.java

SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/
├── repository/TodoRepository.java               ← @Modifying addActualMinutes 쿼리 추가
└── service/TodoService.java                     ← addActualMinutes(userId, todoId, minutes) 메서드 추가

SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/
└── TimerSessionController.java                  ← POST/GET/GET today-stats

SS-Web/src/main/resources/db/migration/
└── V0_0_39__add_timer_sessions.sql

SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/
└── ErrorCode.java                               ← 5개 추가
```

### 2.1 의존성 흐름
- SS-Web → SS-Study (기존)
- SS-Study/timer → SS-Study/fuel (`FuelService`), SS-Study/todo (`TodoService`) — 같은 모듈 내 직접 호출
- SS-Study → SS-Common, SS-Member (기존)

### 2.2 도메인 결합 결정
`POST /api/timer-sessions`는 한 번의 요청에 3개 도메인이 쓰기를 한다:
1. `timer_sessions` INSERT
2. `user_fuel` UPDATE + `fuel_transactions` INSERT
3. `todos.actual_minutes` UPDATE (todoId 있을 때만)

**선택: 직접 호출 + 단일 트랜잭션**
- TimerSessionService가 FuelService.charge() / TodoService.addActualMinutes()를 같은 `@Transactional` 안에서 직접 호출
- 어느 단계든 실패 시 전체 롤백 (Tier 2 server-validated의 일관성 요구)
- 이벤트 분리는 회원가입→연료초기화처럼 fire-and-forget 성격에만 적합. 타이머 저장은 사용자에게 확정값을 즉시 반환해야 함

### 2.3 핵심 idempotency 트릭
`sessionId`(서버 UUID)를 그대로 `FuelService.charge`의 `transactionId`로 전달.
- 같은 sessionId로 fuel.charge가 두 번 호출돼도 Fuel의 기존 idempotency 로직이 1회만 충전
- timer 세션은 `Idempotency-Key` 헤더 기반 dedup (별도 unique 제약)
- 두 계층이 직교하여 모든 재시도 경로에서 중복 충전 방지

---

## 3. Entity 설계

### 3.1 `TimerSession`

```java
@Entity
@Table(name = "timer_sessions",
       indexes = {
           @Index(name = "idx_timer_sessions_user_started", columnList = "user_id, started_at DESC"),
           @Index(name = "idx_timer_sessions_user_todo", columnList = "user_id, todo_id")
       })
@Checks({
    @Check(name = "chk_timer_duration_positive", constraints = "duration_minutes > 0"),
    @Check(name = "chk_timer_duration_max",      constraints = "duration_minutes <= 1440"),
    @Check(name = "chk_timer_time_order",        constraints = "ended_at > started_at")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimerSession extends BaseTimeEntity {

    /**
     * 서버 생성 UUID. Fuel transactionId로 재사용되어 충전 idempotency를 보장한다.
     */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** nullable — Todo 없이 타이머만 사용 가능 (스펙) */
    @Column(name = "todo_id", length = 36)
    private String todoId;

    /** nullable — Todo 삭제 후에도 표시 가능하도록 저장 시점의 스냅샷 */
    @Column(name = "todo_title", length = 100)
    private String todoTitle;

    /**
     * UTC LocalDateTime. 모든 타임스탬프는 UTC 기준 저장 (스펙 00_common.md).
     * 서비스 진입 시점에 Instant → LocalDateTime (UTC) 변환.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /**
     * Idempotency-Key 헤더 값. (user_id, idempotency_key) 부분 unique 인덱스.
     * null이면 매번 신규 세션으로 취급.
     */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    public static TimerSession of(String id, Long userId, String todoId, String todoTitle,
                                  LocalDateTime startedAt, LocalDateTime endedAt,
                                  int durationMinutes, String idempotencyKey) {
        return TimerSession.builder()
                .id(id).userId(userId).todoId(todoId).todoTitle(todoTitle)
                .startedAt(startedAt).endedAt(endedAt)
                .durationMinutes(durationMinutes)
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
```

### 3.2 설계 포인트
- `BaseTimeEntity` 상속 → `created_at`, `updated_at` 자동
- `todoId`는 **DB FK 안 검** — Todo 삭제 후에도 세션 기록 유지 (스펙 의도). 서비스 레이어에서만 본인 todo 소유권 검증
- DB CHECK 제약으로 무결성 1차 방어 (서비스 검증과 이중 방어)
- 인덱스 2개
  - `(user_id, started_at DESC)` — 목록 조회 / today-stats / streak 쿼리 핵심
  - `(user_id, todo_id)` — todoId 필터 + 후속 Todo 도메인 통계 확장 대비

---

## 4. 마이그레이션 — `V0_0_39__add_timer_sessions.sql`

```sql
CREATE TABLE IF NOT EXISTS timer_sessions (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    todo_id           VARCHAR(36),
    todo_title        VARCHAR(100),
    started_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP    NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    idempotency_key   VARCHAR(80),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_timer_sessions_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_timer_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_timer_duration_max      CHECK (duration_minutes <= 1440),
    CONSTRAINT chk_timer_time_order        CHECK (ended_at > started_at)
);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_started
    ON timer_sessions (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_todo
    ON timer_sessions (user_id, todo_id);

-- Idempotency: 동일 (user, key) 중복 INSERT 방지. key=NULL은 다중 허용 (부분 unique 인덱스)
CREATE UNIQUE INDEX IF NOT EXISTS uq_timer_sessions_user_idem
    ON timer_sessions (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

- `FK ON DELETE CASCADE` — 회원 탈퇴 시 세션도 함께 삭제 (Fuel과 동일 정책)
- PostgreSQL의 **부분 unique 인덱스**(`WHERE` 절)로 `idempotency_key=NULL` 케이스를 unique 제약에서 제외

---

## 5. DTO

모두 `Record`로 구현. 시간 입출력은 `Instant`(ISO 8601 UTC)로 명확화.

```java
public record TimerSessionCreateRequest(
        @Size(max = 36) String todoId,
        @Size(max = 100) String todoTitle,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @NotNull Integer durationMinutes
) {}

public record TimerSessionResponse(
        String id,
        String todoId,
        String todoTitle,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes
) {
    public static TimerSessionResponse from(TimerSession s) {
        return new TimerSessionResponse(
                s.getId(), s.getTodoId(), s.getTodoTitle(),
                s.getStartedAt().atOffset(ZoneOffset.UTC).toInstant(),
                s.getEndedAt().atOffset(ZoneOffset.UTC).toInstant(),
                s.getDurationMinutes());
    }
}

public record TimerSessionCreateResponse(
        TimerSessionResponse session,
        Integer fuelCharged
) {}

public record TimerSessionListResponse(
        List<TimerSessionResponse> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {
    public static TimerSessionListResponse from(Page<TimerSession> page) {
        return new TimerSessionListResponse(
                page.getContent().stream().map(TimerSessionResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

public record TodayStatsResponse(
        Integer totalMinutes,
        Integer sessionCount,
        Integer streak
) {}
```

### 5.1 검증 어노테이션 정책
- **Bean Validation은 형식 검증만** (`@NotNull`, `@Size`)
- **값 범위 검증은 서비스에서** — `@Min/@Max`를 쓰면 위반 시 모두 `INVALID_INPUT_VALUE`로 동일 처리되어 스펙의 `SESSION_TOO_SHORT`/`SESSION_TOO_LONG`/`INVALID_DURATION` 코드에 도달 불가능
- 결과: DTO에는 `durationMinutes`에 `@NotNull`만 부여하고, 1~1440 범위 검증은 서비스의 `validate()`가 담당

### 5.2 시간 타입 결정
- DTO: `Instant` — zone 정보 없는 `LocalDateTime`과 달리 절대 시점 명시. Jackson이 `2026-04-16T09:00:00Z`를 자연스럽게 매핑
- Entity: `LocalDateTime` (UTC 약속) — 기존 BaseTimeEntity 컨벤션과 일치. Javadoc에 "UTC" 명시
- 변환은 서비스 진입/이탈 경계에서 1회

---

## 6. Service 로직

### 6.1 `TimerSessionService` 골격

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimerSessionService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 300; // 5분
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final TimerSessionRepository sessionRepository;
    private final FuelService fuelService;
    private final TodoService todoService;
    private final Clock clock;  // BeanConfig에서 Clock.systemUTC() 빈 등록

    // ---------- POST ----------

    @Transactional
    public TimerSessionCreateResponse create(
            Long userId, TimerSessionCreateRequest request, String idempotencyKey) {

        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null : idempotencyKey.trim();

        // 1. 조기 dedup
        if (normalizedKey != null) {
            Optional<TimerSession> existing = sessionRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedKey);
            if (existing.isPresent()) {
                log.info("[Timer] idempotent skip | userId={}, key={}, sessionId={}",
                        userId, normalizedKey, existing.get().getId());
                return buildResponse(existing.get(), existing.get().getDurationMinutes());
            }
        }

        // 2. 시간 검증 (5단계, 명시적 ErrorCode)
        LocalDateTime startedAtUtc = LocalDateTime.ofInstant(request.startedAt(), ZoneOffset.UTC);
        LocalDateTime endedAtUtc   = LocalDateTime.ofInstant(request.endedAt(),   ZoneOffset.UTC);
        validate(startedAtUtc, endedAtUtc, request.durationMinutes());

        // 3. 세션 저장 — Idempotency-Key race는 catch + 재조회로 흡수
        String sessionId = UUID.randomUUID().toString();
        TimerSession session = TimerSession.of(
                sessionId, userId,
                request.todoId(), request.todoTitle(),
                startedAtUtc, endedAtUtc, request.durationMinutes(),
                normalizedKey);
        try {
            sessionRepository.save(session);
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey != null) {
                Optional<TimerSession> raced = sessionRepository
                        .findByUserIdAndIdempotencyKey(userId, normalizedKey);
                if (raced.isPresent()) {
                    log.info("[Timer] idempotent race resolved | userId={}, key={}", userId, normalizedKey);
                    return buildResponse(raced.get(), raced.get().getDurationMinutes());
                }
            }
            throw e;
        }

        // 4. Fuel 충전 (sessionId == transactionId → fuel-side idempotency)
        int fuelCharged = request.durationMinutes();
        fuelService.charge(
                userId, fuelCharged, FuelReason.STUDY_SESSION,
                sessionId, sessionId);

        // 5. Todo actualMinutes 누적 (todoId 있을 때만, atomic UPDATE)
        if (request.todoId() != null) {
            todoService.addActualMinutes(userId, request.todoId(), fuelCharged);
        }

        log.info("[Timer] 세션 저장 | userId={}, sessionId={}, duration={}분, todoId={}",
                userId, sessionId, fuelCharged, request.todoId());
        return buildResponse(session, fuelCharged);
    }

    private void validate(LocalDateTime startedAt, LocalDateTime endedAt, int durationMinutes) {
        if (!endedAt.isAfter(startedAt)) {
            throw new CustomException(ErrorCode.INVALID_SESSION_TIME);
        }
        // 분 단위 절삭 기준. 예: 5분 30초 경과 + duration 6분 → INVALID_DURATION
        long elapsedMinutes = Duration.between(startedAt, endedAt).toMinutes();
        if (durationMinutes > elapsedMinutes) {
            throw new CustomException(ErrorCode.INVALID_DURATION);
        }
        if (durationMinutes < 1) {
            throw new CustomException(ErrorCode.SESSION_TOO_SHORT);
        }
        if (durationMinutes > 1440) {
            throw new CustomException(ErrorCode.SESSION_TOO_LONG);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (startedAt.isAfter(now.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS))) {
            throw new CustomException(ErrorCode.FUTURE_SESSION);
        }
    }

    private TimerSessionCreateResponse buildResponse(TimerSession session, int fuelCharged) {
        return new TimerSessionCreateResponse(TimerSessionResponse.from(session), fuelCharged);
    }

    // ---------- GET 목록 ----------

    public TimerSessionListResponse getList(
            Long userId, String startDate, String endDate, String todoId,
            int page, int size) {

        LocalDateTime start = startDate == null ? null
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? null
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<TimerSession> result = sessionRepository.findByFilters(
                userId, start, end, todoId, pageable);
        return TimerSessionListResponse.from(result);
    }

    // ---------- GET today-stats ----------

    public TodayStatsResponse getTodayStats(Long userId) {
        LocalDate todayKst = LocalDate.now(clock.withZone(ZONE_KST));
        LocalDateTime todayStartUtc    = toUtcLdt(todayKst.atStartOfDay(ZONE_KST));
        LocalDateTime tomorrowStartUtc = toUtcLdt(todayKst.plusDays(1).atStartOfDay(ZONE_KST));

        Integer totalMinutes = Optional.ofNullable(
                sessionRepository.sumDurationBetween(userId, todayStartUtc, tomorrowStartUtc))
                .orElse(0);
        long sessionCount = sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId, todayStartUtc, tomorrowStartUtc);

        LocalDateTime lookbackStart = toUtcLdt(
                todayKst.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(ZONE_KST));
        List<LocalDateTime> startedAts = sessionRepository
                .findStartedAtsAfter(userId, lookbackStart);

        int streak = computeStreak(startedAts, todayKst);
        return new TodayStatsResponse(totalMinutes, (int) sessionCount, streak);
    }

    private LocalDateTime toUtcLdt(ZonedDateTime kst) {
        return kst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private int computeStreak(List<LocalDateTime> startedAtsUtc, LocalDate todayKst) {
        TreeSet<LocalDate> studyDays = startedAtsUtc.stream()
                .map(ts -> ts.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE_KST).toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));
        if (studyDays.isEmpty()) return 0;

        LocalDate latest = studyDays.last();
        // clock skew 허용으로 latest가 미래일 수 있음 → 오늘로 클램프
        LocalDate cursor = latest.isAfter(todayKst) ? todayKst : latest;
        // 오늘과 어제 모두 없으면 streak = 0
        if (cursor.isBefore(todayKst.minusDays(1))) return 0;

        int streak = 0;
        while (studyDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
```

### 6.2 검증 5단계 (스펙 매핑)

| 순서 | 검증 | ErrorCode |
|------|------|-----------|
| 1 | `endedAt > startedAt` | `INVALID_SESSION_TIME` |
| 2 | `durationMinutes <= floor((endedAt - startedAt) / 1분)` | `INVALID_DURATION` |
| 3 | `durationMinutes >= 1` | `SESSION_TOO_SHORT` |
| 4 | `durationMinutes <= 1440` | `SESSION_TOO_LONG` |
| 5 | `startedAt <= now + 5분` | `FUTURE_SESSION` |

- 분 단위 절삭은 의도된 동작 (보수적 검증). 클라이언트는 정상적으로 사용 시 영향받지 않음.
- `CLOCK_SKEW_TOLERANCE_SECONDS = 300` (5분) — 모바일 시계 오차 흔하므로 여유

### 6.3 Streak 계산 정책
- **KST(Asia/Seoul) 고정** — 한국 서비스 가정. 글로벌 확장 시 `ZONE_KST` 상수 1곳만 변경
- 최근 365일치 distinct 공부 날짜만 조회하여 메모리에서 계산 — DB 부담 상한
- "오늘 안 했어도 어제까지 streak 유지" 조건 = `cursor >= today-1`이면 카운팅 시작 (스펙 §3)
- 미래 날짜 클램프 — clock skew 허용으로 발생할 수 있는 streak 부풀림 방지

---

## 7. Todo 도메인 보강

`Todo.actualMinutes`는 기존에 `updateActualMinutes(Integer)` (덮어쓰기형)만 존재. 누적 업데이트를 안전하게 처리할 메서드를 추가한다.

### 7.1 `TodoRepository` 변경

```java
@Modifying
@Query("UPDATE Todo t SET t.actualMinutes = COALESCE(t.actualMinutes, 0) + :minutes " +
       "WHERE t.id = :todoId AND t.userId = :userId")
int addActualMinutes(@Param("userId") Long userId,
                     @Param("todoId") String todoId,
                     @Param("minutes") int minutes);
```

### 7.2 `TodoService` 변경

```java
@Transactional
public void addActualMinutes(Long userId, String todoId, int minutes) {
    if (minutes <= 0) {
        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
    }
    int updated = todoRepository.addActualMinutes(userId, todoId, minutes);
    if (updated == 0) {
        throw new CustomException(ErrorCode.TODO_NOT_FOUND);
    }
}
```

### 7.3 변경 근거
- **lost update 방지**: dirty checking 기반 read-modify-write는 동시 두 세션 저장 시 한쪽이 사라짐. atomic SQL UPDATE는 DB 레벨에서 직렬화
- **소유권 검증 통합**: `WHERE t.userId = :userId` 조건으로 본인 todo만 갱신. affected rows == 0 이면 `TODO_NOT_FOUND` (없거나 남의 것)
- **DB 호출 1회**: 별도 `requireOwned()` 메서드 불필요 — 단순화

---

## 8. Controller

```java
@Tag(name = "Timer", description = "공부 타이머 세션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timer-sessions")
public class TimerSessionController {

    private final TimerSessionService timerSessionService;

    @Operation(summary = "세션 기록 저장", description = """
        타이머 종료 시 세션을 저장합니다.
        서버에서 시간 유효성 5단계 검증 후, 통과 시 연료를 자동 충전하고
        연결된 Todo의 actualMinutes를 누적합니다. (단일 트랜잭션)

        ### Idempotency
        헤더 `Idempotency-Key`를 보내면 동일 키 재요청 시 기존 세션을 반환합니다 (중복 충전 방지).
        """)
    @PostMapping
    public ResponseEntity<TimerSessionCreateResponse> create(
            @AuthMember LoginMember loginMember,
            @Valid @RequestBody TimerSessionCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        TimerSessionCreateResponse response = timerSessionService.create(
                loginMember.memberId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "세션 목록 조회")
    @GetMapping
    public ResponseEntity<TimerSessionListResponse> getList(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String todoId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        validateDateParam(startDate);
        validateDateParam(endDate);
        if (page < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (size < 1 || size > 100) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        return ResponseEntity.ok(timerSessionService.getList(
                loginMember.memberId(), startDate, endDate, todoId, page, size));
    }

    @Operation(summary = "오늘 공부 통계", description = "KST 기준 오늘의 총 분/세션 수/연속 일수")
    @GetMapping("/today-stats")
    public ResponseEntity<TodayStatsResponse> getTodayStats(
            @AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(
                timerSessionService.getTodayStats(loginMember.memberId()));
    }

    private void validateDateParam(String date) {
        if (date == null) return;
        try { LocalDate.parse(date); }
        catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
```

### 8.1 Swagger 정책
Fuel/Todo 컨트롤러와 동일 수준의 풀세트:
- `@Operation` description (마크다운, query/header 설명 포함)
- `@ApiResponses` 200/201/400/401/500 각 케이스 + `@ExampleObject` 샘플 본문
- 에러는 `ErrorResponse` schema 참조

---

## 9. Repository

```java
public interface TimerSessionRepository extends JpaRepository<TimerSession, String> {

    Optional<TimerSession> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
        SELECT s FROM TimerSession s
        WHERE s.userId = :userId
          AND (:start  IS NULL OR s.startedAt >= :start)
          AND (:end    IS NULL OR s.startedAt <  :end)
          AND (:todoId IS NULL OR s.todoId = :todoId)
        """)
    Page<TimerSession> findByFilters(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("todoId") String todoId,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start AND s.startedAt < :end")
    Integer sumDurationBetween(@Param("userId") Long userId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    long countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s.startedAt FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start")
    List<LocalDateTime> findStartedAtsAfter(@Param("userId") Long userId,
                                            @Param("start") LocalDateTime start);
}
```

- 종료일은 Fuel과 동일 반열림 `[start, end+1)` 정책
- 인덱스 `(user_id, started_at DESC)`가 모든 쿼리에 활용됨

---

## 10. ErrorCode 추가

`SS-Common/.../ErrorCode.java`에 5개 추가:

```java
// Timer
INVALID_SESSION_TIME(HttpStatus.BAD_REQUEST, "시작 시각이 종료 시각보다 늦거나 같습니다."),
INVALID_DURATION(HttpStatus.BAD_REQUEST, "공부 시간이 시작/종료 시각 간격보다 큽니다."),
SESSION_TOO_SHORT(HttpStatus.BAD_REQUEST, "공부 시간은 1분 이상이어야 합니다."),
SESSION_TOO_LONG(HttpStatus.BAD_REQUEST, "공부 시간은 24시간(1440분)을 초과할 수 없습니다."),
FUTURE_SESSION(HttpStatus.BAD_REQUEST, "미래 시각의 세션은 저장할 수 없습니다."),
```

### 10.1 전체 에러 매트릭스

| 상황 | HTTP | code |
|------|------|------|
| 토큰 없음/만료 | 401 | `UNAUTHENTICATED_REQUEST` / `ACCESS_TOKEN_EXPIRED` |
| `@NotNull` / `@Size` 위반 | 400 | `INVALID_INPUT_VALUE` |
| 본문 파싱 실패 | 400 | `INVALID_REQUEST_BODY` |
| `startedAt >= endedAt` | 400 | `INVALID_SESSION_TIME` |
| `durationMinutes > 경과시간` | 400 | `INVALID_DURATION` |
| `durationMinutes < 1` | 400 | `SESSION_TOO_SHORT` |
| `durationMinutes > 1440` | 400 | `SESSION_TOO_LONG` |
| `startedAt > now + 5분` | 400 | `FUTURE_SESSION` |
| todoId 본인 소유 아님 / 없음 | 404 | `TODO_NOT_FOUND` |
| 잘못된 date/page/size 파라미터 | 400 | `INVALID_INPUT_VALUE` |
| Fuel 미초기화 (이론상 X) | 500 | `FUEL_NOT_INITIALIZED` |

---

## 11. 빈 설정

### 11.1 `Clock` 빈 등록
`SS-Web/.../config/BeanConfig.java` (없으면 신규):

```java
@Configuration
public class BeanConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- 서비스의 `now()`와 KST 변환 모두 이 빈을 통과
- 테스트는 `@TestConfiguration`에서 `Clock.fixed(...)` 주입으로 결정적 검증

---

## 12. 데이터 플로우

### 12.1 정상 흐름 (POST)

```
Client
  ↓ Authorization: Bearer ...
  ↓ Idempotency-Key: <UUID>   (선택)
  ↓ Body: { todoId, todoTitle, startedAt, endedAt, durationMinutes }
TimerSessionController.create()
  ↓ @Valid (NotNull/Size만)
TimerSessionService.create()   [Tx 시작]
  ├─ 1. idempotencyKey 정규화 (blank → null)
  ├─ 2. 조기 dedup 조회 (있으면 즉시 응답)
  ├─ 3. Instant → LocalDateTime UTC 변환
  ├─ 4. validate() — 5단계 명시적 ErrorCode
  ├─ 5. sessionId = UUID
  ├─ 6. try sessionRepository.save(session)
  │       catch DataIntegrityViolation → 재조회 dedup or rethrow
  ├─ 7. fuelService.charge(amount=duration, txId=sessionId)
  │       └─ user_fuel SELECT FOR UPDATE + 충전 + fuel_transactions INSERT
  │       └─ (이미 존재하면 idempotent return)
  ├─ 8. if (todoId != null)
  │       todoService.addActualMinutes(userId, todoId, amount)
  │       └─ atomic UPDATE, affected==0 → TODO_NOT_FOUND
  └─ 9. buildResponse(session, fuelCharged)   [Tx 커밋]
```

### 12.2 실패 시 동작
- 어느 단계든 예외 발생 → 전체 트랜잭션 롤백 → 세션/충전/누적 모두 없던 일
- 사용자가 같은 `Idempotency-Key`로 재시도 → 새로 처리 (이전 시도가 롤백되었으므로 신규)
- 같은 `Idempotency-Key`로 재시도했는데 이전 시도가 성공이었다면 → 2번 단계에서 dedup 반환

---

## 13. 테스트 전략

### 13.1 SS-Study 단위/통합

**`TimerSessionTest`** — Entity static factory 동작

**`TimerSessionRepositoryTest`** — `TestApplication + @ImportAutoConfiguration` 패턴 (Spring Boot 4 제약 우회, 기존 Fuel/Todo Repository 테스트와 동일)
- `findByFilters` null/실값 매트릭스 (start/end/todoId 각각)
- `sumDurationBetween` 빈 결과 → 0, 정상 합산
- `findByUserIdAndIdempotencyKey` 존재/부재
- 부분 unique 인덱스: 같은 (user, key) 중복 INSERT → 실패, key=NULL 중복 → 허용
- `findStartedAtsAfter` 정렬/필터링

**`TimerSessionServiceTest`** (Mockito + `Clock.fixed`)
- 검증 5케이스 각각 (`@ParameterizedTest` 권장)
- 정상 저장 → `fuelService.charge` 호출 검증 (sessionId == transactionId 인자 매칭)
- todoId 있을 때 → `todoService.addActualMinutes` 호출
- todoId 없을 때 → todo 관련 호출 0회
- Idempotency-Key 재요청 → 기존 세션 반환, fuel/todo 호출 0회
- Idempotency-Key blank/null → null로 정규화
- Idempotency race 시뮬레이션 (save 시 DataIntegrityViolation → 재조회) → 기존 세션 반환
- Clock skew 경계값 (5분 ±1초)
- Streak 케이스 매트릭스:
  - 빈 데이터 → 0
  - 오늘만 1세션 → 1
  - 오늘 + 어제 → 2
  - 오늘 없고 어제까지 N일 연속 → N
  - 오늘도 어제도 없음 → 0
  - 중간 단절 (오늘, 어제, 그저께 빈, 그 전 연속) → 2
  - latest가 미래 (clock skew) → 클램프 동작 확인
  - 365일 경계 — lookback 상한 안에서만 카운팅

**`TodoServiceTest`** 추가 케이스
- `addActualMinutes` — 신규 todo (null → 0+minutes)
- 기존 todo (기존+minutes)
- 본인 소유 아님 → `TODO_NOT_FOUND`
- 존재하지 않는 todoId → `TODO_NOT_FOUND`
- minutes <= 0 → `INVALID_INPUT_VALUE`

### 13.2 SS-Web Controller (MockMvc)

**`TimerSessionControllerTest`**
- POST 201 정상
- POST 400 — 각 ErrorCode별 (총 5개 + INVALID_INPUT_VALUE + INVALID_REQUEST_BODY)
- POST 404 — 없는 todoId
- POST 401 — 인증 없음
- POST `Idempotency-Key` 유/무 비교 (서비스 인자 검증)
- GET 200 — 페이지네이션, 필터 조합
- GET 400 — 잘못된 query (date 포맷, page<0, size>100)
- GET `/today-stats` 200 정상

### 13.3 테스트용 Clock 주입
`@TestConfiguration`에서 `Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC)` 빈 등록 → 모든 시간 의존 검증을 결정적으로 처리

---

## 14. 작업 순서 (커밋 단위 가이드)

각 커밋 메시지: `타이머 세션 도메인 구현 : {type} : {설명} #25`

```
1. chore : version.yml 0.0.38 → 0.0.39
           V0_0_39__add_timer_sessions.sql
           ErrorCode 5개 추가
           Clock 빈 등록 (BeanConfig)
2. feat  : TimerSession Entity + TimerSessionRepository + 통합 테스트
3. feat  : DTO + TimerSessionService (검증/저장/목록/통계/streak) + 단위 테스트
4. feat  : TodoRepository.addActualMinutes + TodoService.addActualMinutes + 회귀 테스트
5. feat  : TimerSessionController + Swagger + Controller 테스트
6. docs  : CLAUDE.md 마이그레이션 이력 표에 0.0.39 추가
```

---

## 15. 위험 요소 및 완화

| 위험 | 영향 | 완화 |
|------|------|------|
| Fuel 충전 실패 → 세션 롤백 후 사용자 재전송 | 충전 누락 / 중복 | sessionId 재사용 + Fuel-side idempotency + Idempotency-Key 헤더 두 계층 |
| 외부 조작 todoId (남의 Todo) | 다른 사용자 actualMinutes 오염 | atomic UPDATE의 `WHERE userId = :userId` 조건 → affected==0 → TODO_NOT_FOUND |
| Idempotency-Key 동시 race | 일시적 500 | DataIntegrityViolation catch → 재조회 패턴 |
| 같은 todoId 동시 두 세션 → actualMinutes lost update | 누적 누락 | atomic SQL UPDATE (`COALESCE + :minutes`)로 DB 레벨 직렬화 |
| Streak 쿼리 비대화 | 1년 이상 사용자 쿼리 비용 증가 | `STREAK_LOOKBACK_DAYS = 365` 상한 |
| KST 고정 → 글로벌 확장 시 영향 | 재설계 필요 | `ZONE_KST` 상수 1곳에 집중, 추후 timezone 인자화 쉬움 |
| Fuel 미초기화 회원 (이론상 X) | 500 노출 | 기존 `MemberCreatedEvent` 보장 + `FUEL_NOT_INITIALIZED` 코드 유지 |
| Clock 빈 미주입 | 기동 실패 | `BeanConfig`에 `Clock.systemUTC()` 명시 등록 |
| Bean Validation으로 인한 ErrorCode 손실 | 스펙 위배 | DTO에 `@Min/@Max` 미사용, 서비스가 명시적 검증 |
| `LocalDateTime` zone 모호성 | 타임존 버그 | DTO는 `Instant`, Entity는 UTC LocalDateTime + Javadoc 명시 |

---

## 16. Definition of Done

- [ ] 3개 엔드포인트 모두 정상 동작 (Swagger 수동 검증)
- [ ] 검증 5케이스 모두 정확한 ErrorCode 반환
- [ ] Fuel 잔량/거래내역에 `STUDY_SESSION` 정상 반영
- [ ] `Todo.actualMinutes` 누적 (null → 0+분, 기존 → 기존+분), 같은 todoId 동시 2세션 시 누락 없음
- [ ] `Idempotency-Key` 재요청 시 fuel/todo 변화 없음, 같은 세션 ID로 응답
- [ ] today-stats: 빈 데이터, 단일 세션, 다세션, streak 0/1/N 케이스 통과
- [ ] 잘못된 todoId(남의 것, 존재 안 함) → 404
- [ ] 모든 단위/통합 테스트 통과 (`./gradlew test`)
- [ ] CLAUDE.md 마이그레이션 표 0.0.39 추가
- [ ] version.yml 0.0.39 반영

---

## 부록 A. 응답 예시

### POST /api/timer-sessions (201)
```json
{
  "session": {
    "id": "8f3c2b4d-1234-4abc-9def-0123456789ab",
    "todoId": "todo-uuid-5678",
    "todoTitle": "수학 문제 풀기",
    "startedAt": "2026-05-25T00:00:00Z",
    "endedAt": "2026-05-25T01:30:00Z",
    "durationMinutes": 90
  },
  "fuelCharged": 90
}
```

### GET /api/timer-sessions (200)
```json
{
  "content": [
    {
      "id": "8f3c2b4d-...",
      "todoId": "todo-uuid-5678",
      "todoTitle": "수학 문제 풀기",
      "startedAt": "2026-05-25T00:00:00Z",
      "endedAt": "2026-05-25T01:30:00Z",
      "durationMinutes": 90
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

### GET /api/timer-sessions/today-stats (200)
```json
{
  "totalMinutes": 180,
  "sessionCount": 3,
  "streak": 7
}
```
