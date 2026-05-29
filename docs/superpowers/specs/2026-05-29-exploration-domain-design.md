# 탐험(Exploration) 도메인 설계 (frontend 계약 정합 버전)

> 작성일: 2026-05-29 (개정)
> 대상 API 스펙: `docs/api-specs/05_exploration.md`
> 프론트 계약: `docs/api-specs/exploration-frontend-requirements.md`
> 프론트 시드 원본: Flutter 레포 `lib/features/exploration/data/seed/exploration_seed_data.dart`
> 동기화 Tier: 2 (Server-Validated)

---

## 0. 개정 배경

초기 구현은 대화 중 임의로 정한 로스터(달 포함, 천왕성 누락)·아이콘(`mars-mountain` 등 프론트 미인식 값)·연료·ID(`region-kr`)를 사용해 **프론트 계약과 어긋났다.** 본 개정판은 **서버 시드를 프론트 게스트 시드와 1:1로 일치**시키고, 프론트가 요구한 `INSUFFICIENT_FUEL` 응답 보강을 추가한다. 구조 코드(entity/repository/service/controller/DTO)는 계약을 이미 충족하므로 형태를 유지하되, 작업은 working tree를 폐기하고 깨끗한 상태에서 재구현한다.

---

## 1. 개요

행성(planet) → 지역(region) 2단계 트리를 연료로 해금한다. 이전 행성을 **클리어**(모든 하위 지역 해금)해야 다음 행성을 해금할 수 있는 **진행 게이트**(`prerequisiteId`)를 둔다. 게스트(로컬)와 회원(서버)은 완전히 분리되며 마이그레이션은 없다.

### 엔드포인트 (4개)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/explorations/planets` | 행성 목록 + 유저 해금/클리어/진행도 |
| 2 | GET | `/api/explorations/planets/{planetId}/regions` | 행성 하위 지역 목록 + 유저 해금 상태 |
| 3 | POST | `/api/explorations/regions/{regionId}/unlock` | 지역 해금 (연료 차감) |
| 4 | POST | `/api/explorations/planets/{planetId}/unlock` | 행성 해금 (연료 차감 + 선행 게이트) |

---

## 2. 핵심 설계 결정

| 결정 | 선택 | 근거 |
|------|------|------|
| 기본 해금 표현 | 암묵적 (`requiredFuel == 0`) | per-user 시드/리스너/백필 불필요. earth·korea가 해당 |
| 행성 isCleared / progress | 조회 시 파생(derive) | 저장 안 함. 하위 region 마스터 수와 유저 해금 수를 메모리 집계 |
| region isCleared | = isUnlocked | 해금 = 클리어 |
| 진행 게이트 | 명시적 `prerequisiteNodeId` 컬럼, 행성만 | sortOrder 체인. 선행 행성 클리어 필수 |
| 해금 멱등성 | UNIQUE(user_id, node_id) + 단일 트랜잭션 | 동시 중복 시 제약 위반→롤백(연료 포함). transactionId는 매 호출 신규 UUID |
| 시드 출처 | **프론트 게스트 시드 1:1 미러** | 게스트/회원 코드·아이콘·진행감 일치 |
| INSUFFICIENT_FUEL 응답 | `requiredFuel`/`currentFuel` 동봉 | 프론트가 정확한 안내 문구 생성 |

### 환율 컨텍스트
`UserFuel.MINUTES_PER_FUEL = 30` (30분 공부 = 1 연료). 연료 수치는 프론트 시드 값을 그대로 사용한다(서버가 임의 재산정하지 않음).

---

## 3. 모듈 / 패키지 배치

`fuel`/`timer`/`todo`와 동일하게 **SS-Study**, Controller만 **SS-Web**.

```
SS-Study/.../study/exploration/
├── constant/   NodeType (PLANET, REGION), NodeTypeConverter
├── dto/        PlanetResponse, RegionResponse, ProgressDto,
│               RegionUnlockResponse, PlanetUnlockResponse, UnlockedNodeDto
├── entity/     ExplorationNode (마스터, read-only), UserExploration (유저 진행)
├── repository/ ExplorationNodeRepository, UserExplorationRepository
└── service/    ExplorationService

SS-Web/.../controller/exploration/ExplorationController

SS-Common/.../common/exception/
├── ErrorCode.java                (탐험 에러 5종 추가)
├── ErrorResponse.java            (nullable requiredFuel/currentFuel 추가)
├── InsufficientFuelException.java(신규)
└── GlobalExceptionHandler.java   (InsufficientFuelException 분기 추가)
```

---

## 4. Entity

### ExplorationNode (마스터, 시드 전용·읽기 전용)
- `@Entity @Table(name="exploration_nodes")`, `@Getter @Builder @AllArgsConstructor @NoArgsConstructor(PROTECTED)`. **BaseTimeEntity 미상속.**

| 필드 | 타입 | 컬럼 |
|------|------|------|
| `id` | String `@Id` | id (고정 문자열, 예 `mars_olympus`) |
| `name` | String | name |
| `nodeType` | NodeType (`@Convert` 소문자) | node_type |
| `depth` | int | depth (planet=2, region=3) |
| `icon` | String | icon |
| `parentId` | String (nullable) | parent_id |
| `prerequisiteNodeId` | String (nullable) | prerequisite_node_id |
| `requiredFuel` | int | required_fuel |
| `sortOrder` | int | sort_order |
| `description` | String | description |
| `mapX` | double | map_x |
| `mapY` | double | map_y |

### UserExploration (유저 진행)
- `@Entity @Table(name="user_exploration_progress", uniqueConstraints=@UniqueConstraint(name="uq_user_expl", columnNames={"user_id","node_id"}))`, BaseTimeEntity 상속.
- 행 존재 = 해금. 정적 팩토리 `unlock(userId, nodeId, cleared)` (isUnlocked=true, unlockedAt=now).

| 필드 | 타입 | 컬럼 |
|------|------|------|
| `id` | Long `@GeneratedValue(IDENTITY)` | id |
| `userId` | Long | user_id |
| `nodeId` | String | node_id |
| `isUnlocked` | boolean | is_unlocked (항상 true) |
| `isCleared` | boolean | is_cleared (region=true, planet=false) |
| `unlockedAt` | LocalDateTime | unlocked_at |

---

## 5. NodeType + Converter

```java
public enum NodeType { PLANET, REGION;
    public String value() { return name().toLowerCase(); }
    public static NodeType from(String v) { return valueOf(v.toUpperCase()); }
}
```
`@Converter` `NodeTypeConverter implements AttributeConverter<NodeType,String>` — DB에는 소문자('planet'/'region') 저장(시드·CHECK·JSON과 일치).

---

## 6. 서비스 로직

`ExplorationService` (`@Transactional(readOnly=true)` 기본, 해금 메서드만 `@Transactional`). 의존성: `ExplorationNodeRepository`, `UserExplorationRepository`, `FuelService`.

### 6.1 GET 행성 목록
```
planets = nodeRepo.findByNodeType(PLANET) (sortOrder asc)
regions = nodeRepo.findByNodeType(REGION)
progress = userExplRepo.findByUserId(userId) → Map<nodeId, UserExploration>
각 planet:
  isUnlocked = requiredFuel==0 || progress.containsKey(id)
  total = 자식 region 수 ; cleared = 자식 region 중 progress에 있는 수
  isCleared = total>0 && cleared==total
  progressRatio = total==0 ? 0.0 : cleared/total
  unlockedAt = progress 행의 값(없으면 null)
  prerequisiteId = prerequisiteNodeId
→ PlanetResponse 리스트
```

### 6.2 GET 지역 목록
```
planet 존재·PLANET 확인 (아니면 PLANET_NOT_FOUND)
regions = nodeRepo.findByParentId(planetId) (sortOrder asc)
각 region: isUnlocked = requiredFuel==0 || progress 존재; isCleared = isUnlocked
→ RegionResponse 리스트
```

### 6.3 POST 지역 해금 — `@Transactional`
```
1. region 조회·REGION 확인 (아니면 REGION_NOT_FOUND)
2. 부모 행성 해금? (requiredFuel==0 || progress 존재) → 아니면 PLANET_LOCKED
3. 이미 해금? (requiredFuel==0 || progress 존재) → ALREADY_UNLOCKED
4. 잔량 pre-check: currentFuel < requiredFuel → InsufficientFuelException(requiredFuel, currentFuel)
5. fuelService.consume(userId, requiredFuel, EXPLORATION_UNLOCK, regionId, UUID)  (원자적 최종 검증)
6. UserExploration.unlock(userId, regionId, true) save
7. planetCleared = isPlanetCleared(userId, 부모행성id) (save 후 재집계)
→ RegionUnlockResponse(region, fuelConsumed=tx.amount, currentFuel=tx.balanceAfter, planetCleared)
```

### 6.4 POST 행성 해금 — `@Transactional`
```
1. planet 조회·PLANET 확인 (아니면 PLANET_NOT_FOUND)
2. 이미 해금? (requiredFuel==0 || progress 존재) → ALREADY_UNLOCKED
3. prerequisiteNodeId != null 이면 선행 행성 isPlanetCleared 확인 → 아니면 PREREQUISITE_NOT_CLEARED
4. 잔량 pre-check → 부족 시 InsufficientFuelException(requiredFuel, currentFuel)
5. fuelService.consume(...) → 6. save(cleared=false)
→ PlanetUnlockResponse(planet, fuelConsumed, currentFuel)
```

> **원자성:** unlock 메서드(@Transactional)가 consume(@Transactional)을 호출 → 동일 트랜잭션 합류. 연료 차감·거래내역·해금행 insert가 한 단위. 잔량 pre-check는 풍부한 에러 본문을 위한 것이고, 경합 시 최종 보증은 consume 내부의 락+검증이 담당.

> **잔량 조회:** pre-check는 `fuelService.getFuel(userId).currentFuel()` 사용.

`isPlanetCleared(userId, planetId)`: 하위 region이 비면 false, 아니면 모든 region이 해금됐는지 `allMatch`.

---

## 7. DTO (record, dto/)

```java
record PlanetResponse(String id, String name, String nodeType, int depth, String icon,
                      String parentId, String prerequisiteId, int requiredFuel,
                      boolean isUnlocked, boolean isCleared, int sortOrder,
                      String description, double mapX, double mapY,
                      String unlockedAt, ProgressDto progress) {}
record RegionResponse(String id, String name, String nodeType, int depth, String icon,
                      String parentId, int requiredFuel, boolean isUnlocked, boolean isCleared,
                      int sortOrder, String description, double mapX, double mapY, String unlockedAt) {}
record ProgressDto(int clearedChildren, int totalChildren, double progressRatio) {}
record UnlockedNodeDto(String id, String name, boolean isUnlocked, boolean isCleared, String unlockedAt) {}
record RegionUnlockResponse(UnlockedNodeDto region, int fuelConsumed, int currentFuel, boolean planetCleared) {}
record PlanetUnlockResponse(UnlockedNodeDto planet, int fuelConsumed, int currentFuel) {}
```
`nodeType` 소문자(`value()`), `unlockedAt` ISO-8601 UTC. 정적 `of(...)` 팩토리 사용.

---

## 8. 에러 처리

### 8.1 ErrorCode 추가 (5종)
```java
PLANET_NOT_FOUND(NOT_FOUND, "해당 행성을 찾을 수 없습니다."),
REGION_NOT_FOUND(NOT_FOUND, "해당 지역을 찾을 수 없습니다."),
ALREADY_UNLOCKED(BAD_REQUEST, "이미 해금된 노드입니다."),
PLANET_LOCKED(BAD_REQUEST, "상위 행성이 아직 해금되지 않았습니다."),
PREREQUISITE_NOT_CLEARED(BAD_REQUEST, "이전 행성을 먼저 클리어해야 합니다."),
```
`INSUFFICIENT_FUEL`은 기존 것 재사용.

### 8.2 INSUFFICIENT_FUEL 응답 보강
- `ErrorResponse` record에 nullable `Integer requiredFuel`, `Integer currentFuel` 추가 + 클래스에 `@JsonInclude(JsonInclude.Include.NON_NULL)` → 기존 응답(두 필드 null)은 직렬화에서 생략되어 **기존 계약 불변**.
  - 기존 `of(ErrorCode)` / `of(ErrorCode, message)`는 두 필드 null로 생성. 신규 `of(ErrorCode, requiredFuel, currentFuel)` 추가.
- `InsufficientFuelException extends RuntimeException` (필드 requiredFuel, currentFuel) 신규.
- `GlobalExceptionHandler`에 `@ExceptionHandler(InsufficientFuelException.class)` 추가 → 400 + `{code:"INSUFFICIENT_FUEL", message, requiredFuel, currentFuel}`.
- 응답 예시:
```json
{ "code": "INSUFFICIENT_FUEL", "message": "연료가 부족합니다.", "requiredFuel": 10, "currentFuel": 4 }
```

---

## 9. Flyway 마이그레이션

`SS-Web/src/main/resources/db/migration/V0_0_42__add_exploration.sql`
(version.yml 현재 `0.0.42`. 구현 시점에 CI가 올렸으면 그 값으로. 한 버전당 1파일.)

### 스키마
```sql
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
```

### 시드 — 행성 (8, 행성 먼저 INSERT, `ON CONFLICT (id) DO NOTHING`)

| id | name | icon | required_fuel | prerequisite | sort | map_x | map_y | description |
|---|---|---|---|---|---|---|---|---|
|earth|지구|earth|0|NULL|0|0.5|0.08|우리의 출발지, 고향 행성|
|mercury|수성|mercury|3|earth|1|0.15|0.20|태양에 가장 가까운 작은 행성|
|venus|금성|venus|5|mercury|2|0.75|0.32|두꺼운 대기로 뒤덮인 뜨거운 행성|
|mars|화성|mars|10|venus|3|0.25|0.44|붉은 행성, 탐험의 꿈|
|jupiter|목성|jupiter|20|mars|4|0.7|0.56|태양계 최대의 가스 행성|
|saturn|토성|saturn|30|jupiter|5|0.2|0.68|아름다운 고리를 가진 행성|
|uranus|천왕성|uranus|45|saturn|6|0.8|0.80|옆으로 누워 자전하는 얼음 행성|
|neptune|해왕성|neptune|60|uranus|7|0.35|0.92|태양계 끝자락의 푸른 행성|

### 시드 — 지역 (30, depth=3, prerequisite=NULL, map_x=0, map_y=0)

**earth (12)** — icon=국가코드:
| id | name | icon | fuel | sort | description |
|---|---|---|---|---|---|
|korea|대한민국|KR|0|0|한반도 남쪽, K-컬쳐의 중심|
|japan|일본|JP|1|1|벚꽃과 기술의 나라|
|thailand|태국|TH|1|2|미소의 나라, 동남아의 허브|
|china|중국|CN|2|3|세계 최대 인구 대국|
|india|인도|IN|2|4|IT 강국, 다양한 문화의 보고|
|uk|영국|GB|2|5|해가 지지 않는 나라|
|france|프랑스|FR|2|6|예술과 낭만의 나라|
|canada|캐나다|CA|2|7|단풍과 자연의 나라|
|usa|미국|US|3|8|자유의 나라, 기회의 땅|
|brazil|브라질|BR|3|9|삼바와 축구의 나라|
|australia|호주|AU|3|10|코알라와 캥거루의 대륙|
|egypt|이집트|EG|2|11|피라미드와 나일강의 나라|

**나머지 행성 지역 (18)** — icon=행성이름:
| id | parent | name | icon | fuel | sort | description |
|---|---|---|---|---|---|---|
|mercury_caloris|mercury|칼로리스 분지|mercury|1|0|수성 최대의 충돌 분지|
|mercury_plains|mercury|북극 평원|mercury|2|1|얼음이 숨겨진 영구 그림자 지대|
|venus_ishtar|venus|이슈타르 대지|venus|2|0|금성 북반구의 거대한 고원 지대|
|venus_aphrodite|venus|아프로디테 대지|venus|3|1|금성 적도를 따라 펼쳐진 최대 대지|
|venus_maxwell|venus|맥스웰 산|venus|3|2|금성에서 가장 높은 산맥|
|mars_olympus|mars|올림푸스 산|mars|3|0|태양계에서 가장 높은 화산|
|mars_valles|mars|마리너 계곡|mars|4|1|태양계 최대의 협곡|
|mars_polar|mars|극관 지대|mars|5|2|드라이아이스와 물 얼음의 극지방|
|jupiter_red_spot|jupiter|대적점|jupiter|5|0|수백 년간 지속되는 거대 폭풍|
|jupiter_europa|jupiter|유로파|jupiter|7|1|얼음 아래 바다가 있는 위성|
|jupiter_io|jupiter|이오|jupiter|8|2|화산 활동이 가장 활발한 위성|
|saturn_rings|saturn|토성 고리|saturn|8|0|얼음과 먼지로 이루어진 아름다운 고리|
|saturn_titan|saturn|타이탄|saturn|10|1|대기를 가진 유일한 위성, 메탄의 호수|
|saturn_enceladus|saturn|엔셀라두스|saturn|12|2|간헐천이 분출하는 얼음 위성|
|uranus_miranda|uranus|미란다|uranus|12|0|기괴한 지형의 작은 위성|
|uranus_atmosphere|uranus|천왕성 대기|uranus|15|1|메탄이 만드는 청록빛 대기|
|neptune_dark_spot|neptune|대흑점|neptune|15|0|초속 2000km 폭풍의 소용돌이|
|neptune_triton|neptune|트리톤|neptune|20|1|역행 궤도를 도는 거대 위성|

총 **행성 8 + 지역 30 = 38 노드.**

> 게이트 영향: mercury 해금하려면 earth의 12개 지역을 모두 해금(클리어)해야 함. 이는 의도된 진행 게이트다.

---

## 10. API 스펙 문서 갱신
`docs/api-specs/05_exploration.md`:
- 노드 객체에 `prerequisiteId` 필드, 행성 해금에 선행조건 + `PREREQUISITE_NOT_CLEARED`.
- DB 테이블 참고에 `prerequisite_node_id`.
- 예시 노드/연료 수치를 본 시드(8행성/30지역, 프론트 값)로 정정. region ID/icon 규칙(이름기반 ID, 국가코드/행성이름 icon) 명시.
- `INSUFFICIENT_FUEL` 응답에 `requiredFuel`/`currentFuel` 포함 명시.

---

## 11. 테스트 전략 (TDD, 80%+)
프론트 Spring Boot 4 test-slice(StudyTestApplication, Testcontainers, create-drop) 사용.
- Entity 단위, Repository(타입/부모/유저 조회 + UNIQUE 위반), Service(Mockito: 목록 파생, 지역해금 정상/PLANET_LOCKED/ALREADY_UNLOCKED/REGION_NOT_FOUND/마지막지역 planetCleared, 행성해금 정상/PREREQUISITE_NOT_CLEARED/ALREADY_UNLOCKED/PLANET_NOT_FOUND, 잔량부족 시 InsufficientFuelException + consume 미호출), Controller(MockMvc 4엔드포인트 + 에러매핑 + INSUFFICIENT_FUEL 본문에 requiredFuel/currentFuel).
- ErrorResponse 직렬화: 두 필드 null이면 생략(@JsonInclude) 검증.

---

## 12. 작업 범위 / 순서
0. **working tree 변경 전부 폐기** (`git checkout -- .` + 미추적 exploration 파일/마이그레이션 삭제, 단 spec/plan 문서는 유지) → main 기준 clean.
1. ErrorCode 5종 + ErrorResponse 보강 + InsufficientFuelException + GlobalExceptionHandler 분기 (SS-Common)
2. NodeType + Converter (SS-Study)
3. ExplorationNode / UserExploration 엔티티
4. Repository 2종 + StudyTestApplication 등록
5. DTO 6종
6. ExplorationService (조회 2 + 해금 2, 잔량 pre-check, FuelService 연동)
7. ExplorationController (SS-Web)
8. Flyway `V0_0_42` (스키마 + 시드 38노드)
9. `docs/api-specs/05_exploration.md` 갱신
10. 단위/통합/컨트롤러 테스트
