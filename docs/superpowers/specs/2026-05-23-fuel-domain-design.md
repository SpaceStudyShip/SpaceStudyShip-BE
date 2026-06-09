# 연료 시스템 도메인 설계 (이슈 #26)

> **이슈**: [연료 시스템 도메인 구현 #26](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/26)
> **브랜치**: `20260422_#26_연료_시스템_도메인_구현`
> **버전**: version.yml `0.0.35` → `0.0.36`
> **마이그레이션**: `V0_0_36__add_fuel.sql`
> **API 스펙**: [docs/api-specs/04_fuel.md](../../api-specs/04_fuel.md)

---

## 1. 개요와 범위

API 스펙의 2개 엔드포인트(`GET /api/fuel`, `GET /api/fuel/transactions`)를 구현하고, 향후 Timer/Exploration 도메인이 호출할 internal API(`FuelService.charge`, `FuelService.consume`)를 함께 제공한다. 동기화 전략은 **Tier 2 (Server-Validated)** — 충전·소비 자체는 서버가 결정하고, 클라이언트는 결과만 조회한다.

### 범위 내
- `UserFuel`, `FuelTransaction` Entity / Repository / Service
- `FuelController` (SS-Web) — GET 2개 엔드포인트
- `FuelService.charge() / consume() / initialize()` internal API
- 신규 회원 가입 시 `UserFuel` 자동 생성 — `MemberCreatedEvent` 이벤트 방식
- Swagger 문서 (Todo/AuthController 수준 풀세트)
- ErrorCode 2개 추가 (`INSUFFICIENT_FUEL`, `FUEL_NOT_INITIALIZED`)
- Flyway 마이그레이션 (`V0_0_36__add_fuel.sql`) + version.yml bump
- 단위/통합 테스트 (Entity, Service, Repository, Controller, Listener)

### 범위 외
- 충전 호출자 (Timer 도메인의 `POST /api/timer-sessions` — 이슈 별도)
- 소비 호출자 (Exploration 도메인 — 이슈 별도)
- `pendingMinutes` 활용 (스펙대로 컬럼만 두고 항상 0)
- 충전/소비 외부 POST 엔드포인트 (보안상 노출하지 않음)

---

## 2. 모듈/패키지 구조

기존 `SS-Study` 모듈에 `fuel` 패키지로 배치. SS-Study가 "학습 도메인 통합 모듈"로 이미 정의되어 있고, Timer/Exploration도 동일 모듈에 합류할 예정이라 자연스러운 위치.

```text
SS-Study/src/main/java/com/elipair/spacestudyship/study/
└── fuel/
    ├── constant/
    │   ├── TransactionType.java        ← Enum (CHARGE, CONSUME)
    │   └── FuelReason.java             ← Enum (STUDY_SESSION, EXPLORATION_UNLOCK)
    ├── dto/
    │   ├── FuelResponse.java
    │   ├── FuelTransactionResponse.java
    │   └── FuelTransactionListResponse.java
    ├── entity/
    │   ├── UserFuel.java
    │   └── FuelTransaction.java
    ├── repository/
    │   ├── UserFuelRepository.java
    │   └── FuelTransactionRepository.java
    └── service/
        ├── FuelService.java
        └── FuelInitializeListener.java

SS-Member/src/main/java/com/elipair/spacestudyship/member/event/
└── MemberCreatedEvent.java             ← record(Long memberId) — SS-Auth가 publish, SS-Study가 listen

SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java
└── findOrCreateMember() 신규 회원 분기에 ApplicationEventPublisher.publishEvent(new MemberCreatedEvent(...)) 추가

SS-Web/src/main/java/com/elipair/spacestudyship/controller/fuel/
└── FuelController.java                 ← GET /api/fuel, GET /api/fuel/transactions
```

### 2.1 의존성 흐름
- SS-Auth → SS-Member (`MemberCreatedEvent` 클래스 참조, 기존 의존 활용)
- SS-Study → SS-Member (기존)
- SS-Web → SS-Study (기존)
- **SS-Auth ↛ SS-Study** (이벤트로 역의존 회피)

### 2.2 이벤트 클래스 위치 결정 근거
SS-Auth는 회원 생성 시점을 알고, SS-Study(및 향후 Badge/Exploration)는 회원 생성에 반응해야 한다. 양쪽 모두 SS-Member를 이미 의존하므로 SS-Member가 자연스러운 공유 컨트랙트 위치. SS-Common 대안도 있으나 도메인 이벤트는 SS-Member가 더 적절.

---

## 3. Entity 설계

### 3.1 UserFuel (1:1, user_id가 PK)

```java
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

- `BaseTimeEntity.updatedAt`이 API 응답의 `lastUpdatedAt` 역할 — 별도 컬럼 없음
- amount ≤ 0 가드는 Entity와 Service 양쪽에서 (방어적)

### 3.2 FuelTransaction

```java
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

- `id`는 호출자가 전달하는 `transactionId` 그대로 사용 (idempotency 키 겸 PK)
- `createdAt`은 `BaseTimeEntity` 활용 — API `createdAt`과 동일

### 3.3 Enum

```java
public enum TransactionType {
    CHARGE,
    CONSUME;
}

public enum FuelReason {
    STUDY_SESSION,        // charge: 공부 세션 완료
    EXPLORATION_UNLOCK;   // consume: 행성/지역 해금
}
```

- API 응답에서 type은 소문자(`"charge"`/`"consume"`) — 응답 매퍼에서 `name().toLowerCase()` 변환
- reason은 대문자 그대로

---

## 4. DTO 설계 (Record + `@Schema`)

### 4.1 FuelResponse

```java
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

### 4.2 FuelTransactionResponse

```java
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

### 4.3 FuelTransactionListResponse (Page envelope)

```java
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

### 4.4 Search 파라미터 — 컨트롤러 `@RequestParam` 직접 사용

별도 Record DTO를 만들지 않고 컨트롤러 메서드 파라미터에 `@RequestParam + @Pattern/@Min/@Max`로 검증. 자세한 시그니처는 §6.1 참조.

### 4.5 시간 포맷 헬퍼 중복
3개 DTO에 `formatUtc(LocalDateTime)` 헬퍼 중복. 기존 Todo 도메인도 동일 중복을 받아들이고 있어 일관성 차원에서 그대로 채택. SS-Common 유틸 분리는 별도 작업.

---

## 5. Repository

### 5.1 UserFuelRepository

```java
public interface UserFuelRepository extends JpaRepository<UserFuel, Long> {

    Optional<UserFuel> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uf FROM UserFuel uf WHERE uf.userId = :userId")
    Optional<UserFuel> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}
```

- `findByUserId` 명시로 의도 표현 (다른 도메인 패턴과 일관성)
- `findByUserIdForUpdate`는 charge/consume 경합 차단용 (Member의 `findByIdForUpdate`와 동일 패턴)
- `existsByUserId`는 이벤트 리스너에서 idempotency 확인용

### 5.2 FuelTransactionRepository

```java
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

### 5.3 날짜 필터 변환 규약
- 입력: `YYYY-MM-DD` 문자열
- `startDate=2026-04-01` → `startDateTime = 2026-04-01 00:00:00`
- `endDate=2026-04-16` → `endDateTime = 2026-04-17 00:00:00` (반열림 `< endDateTime`로 종료일 포함)
- 변환은 서비스 레이어에서 수행

### 5.4 정렬
서비스가 `PageRequest.of(page, size, Sort.by("createdAt").descending())` 강제 주입 (스펙: 최신순 고정)

### 5.5 인덱스 (마이그레이션에서 정의)
```sql
CREATE INDEX idx_fuel_transactions_user_created
    ON fuel_transactions (user_id, created_at DESC);
```

---

## 6. Controller + Service

### 6.1 FuelController

```java
@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Fuel", description = "연료 잔량 및 거래 내역 API")
public class FuelController {

    private final FuelService fuelService;

    @GetMapping("/api/fuel")
    @Operation(summary = "연료 잔량 조회", description = "...")
    @ApiResponses({
        @ApiResponse(responseCode = "200", ...),
        @ApiResponse(responseCode = "401", ...),
        @ApiResponse(responseCode = "500", ...)
    })
    public ResponseEntity<FuelResponse> getFuel(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(fuelService.getFuel(loginMember.memberId()));
    }

    @GetMapping("/api/fuel/transactions")
    @Operation(summary = "연료 거래 내역 조회", description = "...")
    @ApiResponses({
        @ApiResponse(responseCode = "200", ...),
        @ApiResponse(responseCode = "400", ...),
        @ApiResponse(responseCode = "401", ...),
        @ApiResponse(responseCode = "500", ...)
    })
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

### 6.2 Swagger 어노테이션 정책 (Todo/AuthController 패턴)
- 메서드별 `@Operation` (summary + 상세 description)
- 모든 응답 코드(200/400/401/500)에 `@ApiResponse` + `@Schema(implementation = ...)` + `@ExampleObject`
- 에러는 `ErrorResponse` 스키마, `{"code":"...","message":"..."}` 예시

### 6.3 엔드포인트 매핑

| 메소드 | 경로 | 응답 | 주요 에러 |
|-------|-----|-----|---------|
| GET | `/api/fuel` | 200 | 401, 500 |
| GET | `/api/fuel/transactions` (`type`, `startDate`, `endDate`, `page`, `size`) | 200 | 400, 401, 500 |

### 6.4 FuelService — 메서드 시그니처 (개요)

본문은 §6.5~§6.9에 정의. 아래는 시그니처 일람 (실제 구현 시 각 메서드 본문 채움).

```text
@Service @Transactional(readOnly = true)
class FuelService {
    FuelResponse getFuel(Long userId)
    FuelTransactionListResponse getTransactions(Long userId, TransactionType type,
                                                String startDate, String endDate,
                                                int page, int size)
    @Transactional FuelTransactionResponse charge(Long userId, int amount, FuelReason reason,
                                                   String referenceId, String transactionId)
    @Transactional FuelTransactionResponse consume(Long userId, int amount, FuelReason reason,
                                                    String referenceId, String transactionId)
    @Transactional void initialize(Long userId)
}
```

### 6.5 getFuel

```java
public FuelResponse getFuel(Long userId) {
    UserFuel fuel = userFuelRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
    return FuelResponse.from(fuel);
}
```

### 6.6 getTransactions

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

### 6.7 charge (idempotent)

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

### 6.8 consume

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
    fuel.consume(amount);  // 부족 시 INSUFFICIENT_FUEL

    FuelTransaction tx = FuelTransaction.of(
            transactionId, userId, TransactionType.CONSUME,
            amount, reason, referenceId, fuel.getCurrentFuel());
    transactionRepository.save(tx);

    log.info("[Fuel] 소비 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
            userId, amount, reason, transactionId, fuel.getCurrentFuel());
    return FuelTransactionResponse.from(tx);
}
```

### 6.9 initialize (이벤트 리스너에서 호출)

```java
@Transactional
public void initialize(Long userId) {
    if (userFuelRepository.existsByUserId(userId)) {
        log.info("[Fuel] 초기화 스킵 (이미 존재) | userId={}", userId);
        return;
    }
    userFuelRepository.save(UserFuel.initialize(userId));
    log.info("[Fuel] 초기화 | userId={}", userId);
}
```

### 6.10 FuelInitializeListener

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelInitializeListener {

    private final FuelService fuelService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMemberCreated(MemberCreatedEvent event) {
        fuelService.initialize(event.memberId());
    }
}
```

**왜 BEFORE_COMMIT인가**
- 회원 저장 트랜잭션과 동일 트랜잭션에서 처리 → 둘 다 성공/실패로 묶임
- AFTER_COMMIT은 회원만 저장되고 fuel 초기화 실패 시 정합성 깨짐 위험

### 6.11 MemberCreatedEvent (SS-Member)

```java
public record MemberCreatedEvent(Long memberId) { }
```

### 6.12 AuthService 수정 지점

기존 `findOrCreateMember`의 신규 회원 분기:
```java
memberRepository.save(newMember);
eventPublisher.publishEvent(new MemberCreatedEvent(newMember.getId()));  // 추가
```
- `ApplicationEventPublisher` 필드 주입 추가

---

## 7. ErrorCode 추가 (SS-Common)

```java
// Fuel
INSUFFICIENT_FUEL(HttpStatus.BAD_REQUEST, "연료가 부족합니다."),
FUEL_NOT_INITIALIZED(HttpStatus.INTERNAL_SERVER_ERROR, "연료 정보가 초기화되지 않았습니다."),
```

- `INSUFFICIENT_FUEL`: 비즈니스 검증 실패 → 400
- `FUEL_NOT_INITIALIZED`: 시스템 불변식 위반 → 500 (정상 흐름에서 발생 불가)

`GlobalExceptionHandler`가 이미 `CustomException` → `ErrorResponse{code, message}`로 변환하므로 응답 형식 자동 일관.

---

## 8. Flyway 마이그레이션

### 8.1 version.yml bump
`0.0.35` → `0.0.36` (작업 시작 단계에서 변경)

### 8.2 V0_0_36__add_fuel.sql

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

### 8.3 CLAUDE.md 마이그레이션 이력 표 업데이트

| 버전 | 파일 | 내용 |
|------|------|------|
| 0.0.31 | V0_0_31__add_user_devices.sql | 초기 스키마 |
| 0.0.34 | V0_0_34__add_todos_and_categories.sql | todos, todo_categories |
| **0.0.36** | **V0_0_36__add_fuel.sql** | **user_fuel, fuel_transactions 테이블 생성 (CHECK 제약 포함)** |

---

## 9. 테스트 전략

### 9.1 UserFuel Entity 단위 테스트

순수 객체 테스트, Spring 컨텍스트 불필요.

| 시나리오 | 검증 |
|---------|------|
| `initialize(userId)` | 모든 값 0, userId 세팅 |
| `charge(90)` | currentFuel +=90, totalCharged +=90, totalConsumed 불변 |
| `consume(50)` after charge(100) | currentFuel = 50, totalConsumed = 50 |
| `charge(0)` / `charge(-5)` | `CustomException(INVALID_INPUT_VALUE)` |
| `consume(amount > currentFuel)` | `CustomException(INSUFFICIENT_FUEL)` |
| `consume(currentFuel)` (경계값) | currentFuel = 0 |

### 9.2 Repository 테스트 (Testcontainers PostgreSQL)

기존 `StudyTestApplication` 갱신:
```java
@EntityScan(basePackageClasses = {Todo.class, TodoCategory.class, UserFuel.class, FuelTransaction.class, ...})
@EnableJpaRepositories(basePackageClasses = {TodoRepository.class, TodoCategoryRepository.class, UserFuelRepository.class, FuelTransactionRepository.class})
```

**UserFuelRepositoryTest**
- `findByUserId` / `existsByUserId` 기본
- CHECK 제약: `current_fuel = -1` insert 시도 → DataIntegrityViolation
- `findByUserIdForUpdate` smoke (row 반환 확인)

**FuelTransactionRepositoryTest**
- type 필터 (charge만 / consume만 / null 전체)
- 날짜 범위 필터 (startDate / endDate / 둘 다 / 둘 다 null)
- 경계값: `endDate=2026-04-16`로 검색 시 2026-04-16 23:59 거래 포함, 2026-04-17 00:00 거래 제외
- 페이지네이션 + createdAt DESC 정렬
- CHECK 제약: `amount = 0` / 음수 insert 시도 시 실패

### 9.3 Service 단위 테스트 (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class FuelServiceTest {
    @Mock UserFuelRepository userFuelRepository;
    @Mock FuelTransactionRepository transactionRepository;
    @InjectMocks FuelService fuelService;
}
```

| 메서드 | 시나리오 |
|--------|---------|
| `getFuel` | 정상 / `FUEL_NOT_INITIALIZED` |
| `getTransactions` | type=null/CHARGE/CONSUME, startDate/endDate 변환 (LocalDate→LocalDateTime 반열림), Pageable 정렬 검증 (ArgumentCaptor) |
| `charge` | 정상 (락 → entity.charge → save), idempotent 재호출 (기존 tx 반환), `amount<=0` → INVALID_INPUT_VALUE, fuel 부재 → FUEL_NOT_INITIALIZED |
| `consume` | 정상, idempotent, 잔량 부족 → INSUFFICIENT_FUEL, 정확히 잔량만큼 소비 |
| `initialize` | 신규 회원 저장, 이미 존재 시 skip |

**ArgumentCaptor 검증 포인트**
- `transactionRepository.save(captor)` — FuelTransaction의 `balanceAfter`가 실제 잔량 반영
- `transactionRepository.findByFilters(...)` — 날짜 변환 결과가 반열림 `[startDate, endDate+1)` 인지

### 9.4 FuelInitializeListener 단위 테스트

- `MemberCreatedEvent` 수신 시 `fuelService.initialize(memberId)` 호출 검증

### 9.5 AuthService 이벤트 publish 회귀 테스트

기존 `AuthServiceTest`에 추가:
- 신규 회원 로그인 시 `ApplicationEventPublisher.publishEvent` 호출 검증
- 기존 회원 재로그인 시 publish 호출되지 않음 검증

### 9.6 Controller 테스트 (SS-Web, MockMvc)

기존 `TodoControllerTest` 패턴.

| 시나리오 | 검증 |
|---------|------|
| `GET /api/fuel` 200 | FuelResponse JSON 본문 필드 |
| `GET /api/fuel` 401 | 인증 미존재 시 |
| `GET /api/fuel/transactions` 200 | Page envelope JSON 구조 |
| `GET /api/fuel/transactions?type=invalid` | 400 INVALID_INPUT_VALUE |
| `GET /api/fuel/transactions?startDate=2026-13-01` | 400 (Pattern 위반) |
| `GET /api/fuel/transactions?size=200` | 400 (Max 100) |
| `GET /api/fuel/transactions?page=-1` | 400 (Min 0) |
| `GET /api/fuel/transactions?type=charge&startDate=...&endDate=...` | 서비스 호출 인자 검증 (enum 변환, 문자열 그대로 전달) |

### 9.7 통합 시나리오 (yagni)

End-to-end 흐름 (신규 가입→충전→조회→소비→재호출 idempotency)은 Timer/Exploration 도메인 작업 시 자연스럽게 통합 검증. 이번 작업에서는 9.1~9.6만 필수.

### 9.8 커버리지 목표
- 전역 룰(testing.md) 80%+ 준수
- Entity 비즈니스 로직(charge/consume) 100%
- Service 메서드 라인 커버리지 95%+

---

## 10. 셀프 리뷰 체크리스트 (구현 시 확인)

- [ ] `CustomException(ErrorCode)` 던지기 — 직접 ResponseEntity 만들지 않기
- [ ] Service `@Transactional(readOnly = true)` + 쓰기 메소드만 `@Transactional`
- [ ] charge/consume에서 `findByUserIdForUpdate` 사용으로 동시성 방지
- [ ] idempotency: `transactionRepository.findById(transactionId)` 우선 체크 후 기존 결과 반환
- [ ] `amount <= 0` 가드는 Service 진입부와 Entity 내부 모두에
- [ ] Swagger 모든 엔드포인트에 200/400/401/500 응답 명시
- [ ] Query 파라미터에 `@Pattern`/`@Min`/`@Max` 검증, 컨트롤러에 `@Validated`
- [ ] 로그 포맷 `[Fuel] 액션 | key=value` 컨벤션 준수
- [ ] 마이그레이션 파일에 민감한 값 없음
- [ ] version.yml bump 포함된 커밋
- [ ] CLAUDE.md 마이그레이션 이력 표 갱신
- [ ] `StudyTestApplication`에 fuel Entity/Repository 추가
- [ ] `MemberCreatedEvent`는 SS-Member의 `member/event/` 패키지에 위치
- [ ] `AuthService`에 `ApplicationEventPublisher` 필드 주입, `findOrCreateMember` 신규 분기에서 publish

---

## 11. 작업 산출물 요약

| 분류 | 파일 |
|------|------|
| **Entity** | `study/fuel/entity/UserFuel.java`, `FuelTransaction.java` |
| **Enum** | `study/fuel/constant/TransactionType.java`, `FuelReason.java` |
| **DTO** | `study/fuel/dto/FuelResponse.java`, `FuelTransactionResponse.java`, `FuelTransactionListResponse.java` |
| **Repository** | `study/fuel/repository/UserFuelRepository.java`, `FuelTransactionRepository.java` |
| **Service** | `study/fuel/service/FuelService.java`, `FuelInitializeListener.java` |
| **Event** | `SS-Member/.../member/event/MemberCreatedEvent.java` |
| **AuthService 수정** | `findOrCreateMember`에 `publishEvent(...)` 추가 |
| **Controller** | `controller/fuel/FuelController.java` |
| **ErrorCode** | `SS-Common/.../ErrorCode.java` (2개 추가) |
| **Migration** | `SS-Web/.../db/migration/V0_0_36__add_fuel.sql` |
| **version.yml** | `0.0.35` → `0.0.36` |
| **CLAUDE.md** | 마이그레이션 이력 표에 V0_0_36 추가 |
| **Test (SS-Study)** | `UserFuelTest`, `UserFuelRepositoryTest`, `FuelTransactionRepositoryTest`, `FuelServiceTest`, `FuelInitializeListenerTest`, `StudyTestApplication` 갱신 |
| **Test (SS-Auth)** | `AuthServiceTest`에 이벤트 publish 회귀 케이스 추가 |
| **Test (SS-Web)** | `FuelControllerTest` |
