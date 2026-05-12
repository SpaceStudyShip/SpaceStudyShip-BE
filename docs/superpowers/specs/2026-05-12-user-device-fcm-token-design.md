# 디바이스 정보 및 FCM 토큰 저장 기능 설계

- **작성일**: 2026-05-12
- **관련 이슈**: [#23 기능추가][인증] 디바이스 정보 및 FCM 토큰 저장 기능 추가
- **관련 API 스펙**: [docs/api-specs/01_auth.md](../../api-specs/01_auth.md)

---

## 1. 배경 & 문제 정의

### 1.1 현재 상태

- `LoginRequest`는 `socialType`, `idToken`만 받음. 디바이스 정보 없음.
- Refresh Token은 Redis에 `refresh_token:{memberId}` 키로 저장되어 **회원당 1 세션만** 유효.
  - 같은 회원이 다른 디바이스에서 로그인하면 이전 디바이스의 세션이 침묵 무효화됨.
- FCM 토큰을 저장할 장소가 없어 푸시 알림 기능을 구현할 수 없음.
- `db/migration/`에 마이그레이션 파일 없음 (`version.yml`은 `0.0.30`). `members` 테이블은 `hibernate.ddl-auto=update`로만 생성됨.

### 1.2 목표

1. 로그인 요청에 디바이스 정보(`fcmToken`, `deviceType`, `deviceId`)를 받아 저장한다.
2. Refresh Token을 **디바이스별로** 관리하여 다중 디바이스 동시 로그인 세션을 지원한다.
3. 로그아웃 시 해당 디바이스의 세션 정보(refresh token + FCM 토큰)만 정리하고, 다른 디바이스 세션은 유지한다.
4. API 스펙 문서(`docs/api-specs/01_auth.md`)와 코드 구현을 일치시킨다.

### 1.3 비목표 (Out of Scope)

- 실제 FCM 푸시 발송 기능 (별도 이슈).
- Refresh Token 탈취 감지 정책 강화 (현재 수준의 "DB 토큰과 불일치 시 강제 로그아웃"만 유지).
- 디바이스별 권한/푸시 수신 동의 등 디바이스 메타데이터 확장.

---

## 2. 핵심 설계 결정 (Decision Log)

| # | 결정 | 선택지 | 근거 |
|---|------|--------|------|
| D1 | Refresh Token 저장소 | **API 스펙대로 `user_devices.refresh_token`(DB) 통합** | API 스펙이 디바이스별 관리를 가정. Redis 단독 저장은 다중 디바이스 미지원. |
| D2 | `UserDevice` Entity 모듈 | **SS-Auth** | `refresh_token`까지 들어가면 인증 세션 도메인. 기존 `RefreshTokenRepository` 자리를 자연스럽게 대체. SS-Member의 책임 경계 유지. |
| D3 | 디바이스 식별 방식 | **JWT Refresh Token claim에 `did`(deviceId) 포함** | logout/reissue request body 변경 불필요. refresh_token 컬럼에 unique 인덱스 강제 불필요. 토큰 파싱만으로 디바이스 식별 가능. |
| D4 | Unique 제약 | **(member_id, device_id) 컴포지트 unique** | 가족 공유폰/기기 양도 등 같은 디바이스를 다른 계정이 쓰는 시나리오를 별개 row로 자연 수용. |
| D5 | 로그아웃 시 row 처리 | **row 전체 삭제** | "row 존재 = 활성 세션"이라는 단순한 invariant 유지. `WHERE refresh_token IS NOT NULL` 필터 불필요. 재로그인 시 upsert. |
| D6 | FK 정책 | **`ON DELETE CASCADE`** | 회원 탈퇴 시 디바이스 row 자동 정리. `AuthService.withdraw` 로직 단순화. |
| D7 | 마이그레이션 파일 구성 | **`members` baseline + `user_devices`를 한 파일에 작성** (`CREATE TABLE IF NOT EXISTS`) | CLAUDE.md 규칙: 한 version당 1 파일. `members` 마이그레이션이 없는 상태에서 `user_devices`만 추가하면 FK 참조 대상 부재. 이미 ddl-auto로 만들어진 환경에서도 `IF NOT EXISTS`로 안전. |

---

## 3. 데이터 모델

### 3.1 ERD (관련 부분)

```
members (1) ──────< (N) user_devices
   id ◄──FK── member_id
              + (member_id, device_id) UNIQUE
              + ON DELETE CASCADE
```

### 3.2 `user_devices` 스키마

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `member_id` | BIGINT | NOT NULL, FK→members.id ON DELETE CASCADE | |
| `device_id` | VARCHAR(255) | NOT NULL | 클라이언트가 생성/관리하는 디바이스 UUID |
| `device_type` | VARCHAR(10) | NOT NULL | `IOS` / `ANDROID` |
| `fcm_token` | VARCHAR(255) | NOT NULL | Firebase Cloud Messaging 토큰 |
| `refresh_token` | VARCHAR(512) | NOT NULL | 현재 활성 Refresh Token |
| `last_login_at` | TIMESTAMP | NOT NULL | 마지막 로그인 시각 |
| `created_at` | TIMESTAMP | NOT NULL | (BaseTimeEntity) |
| `updated_at` | TIMESTAMP | NOT NULL | (BaseTimeEntity) |

**Constraints:**
- `UNIQUE (member_id, device_id)` — 같은 회원+디바이스 조합은 항상 단일 row.
- `INDEX (member_id)` — 회원 단위 일괄 조회/삭제 시 사용. `refresh_token` 컬럼 인덱스는 불필요 (JWT의 `did` claim으로 디바이스 식별).

### 3.3 마이그레이션 파일

**경로:** `SS-Web/src/main/resources/db/migration/V0_0_31__add_user_devices.sql`

```sql
-- members 테이블 baseline (ddl-auto=update로 이미 존재할 수 있어 IF NOT EXISTS)
CREATE TABLE IF NOT EXISTS members (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    social_id    VARCHAR(100) NOT NULL,
    social_type  VARCHAR(10)  NOT NULL,
    nickname     VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_members_social_id_type UNIQUE (social_id, social_type),
    CONSTRAINT uk_members_nickname UNIQUE (nickname)
);

-- user_devices: 디바이스별 인증 세션 + FCM 토큰
CREATE TABLE IF NOT EXISTS user_devices (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT       NOT NULL,
    device_id     VARCHAR(255) NOT NULL,
    device_type   VARCHAR(10)  NOT NULL,
    fcm_token     VARCHAR(255) NOT NULL,
    refresh_token VARCHAR(512) NOT NULL,
    last_login_at TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_user_devices_member_device UNIQUE (member_id, device_id),
    CONSTRAINT fk_user_devices_member FOREIGN KEY (member_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_devices_member ON user_devices(member_id);
```

> ⚠️ Hibernate가 만든 기존 컬럼/제약과 정확히 일치해야 함. CASCADE 옵션은 Entity의 `@OnDelete(action = OnDeleteAction.CASCADE)`로 명시.

### 3.4 Entity

**`SS-Auth/src/main/java/.../auth/entity/UserDevice.java`**

```java
@Entity
@Table(
    name = "user_devices",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_devices_member_device",
        columnNames = {"member_id", "device_id"}
    ),
    indexes = @Index(name = "idx_user_devices_member", columnList = "member_id")
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(nullable = false, length = 255)
    private String fcmToken;

    @Column(nullable = false, length = 512)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime lastLoginAt;

    public static UserDevice register(Long memberId, String deviceId, DeviceType deviceType,
                                      String fcmToken, String refreshToken) {
        return UserDevice.builder()
            .memberId(memberId)
            .deviceId(deviceId)
            .deviceType(deviceType)
            .fcmToken(fcmToken)
            .refreshToken(refreshToken)
            .lastLoginAt(LocalDateTime.now())
            .build();
    }

    public void renewLogin(DeviceType deviceType, String fcmToken, String refreshToken) {
        this.deviceType = deviceType;
        this.fcmToken = fcmToken;
        this.refreshToken = refreshToken;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void rotateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
```

**설계 노트:**
- `Member`와의 관계는 `@ManyToOne` 매핑 없이 `memberId` 컬럼만 사용. SS-Auth가 SS-Member의 도메인 모델에 강결합되지 않도록 함. 조회 시 Member가 필요하면 `MemberRepository`로 별도 조회.
- 정적 팩토리 `register()` (신규 디바이스) / 인스턴스 메서드 `renewLogin()` (재로그인) / `rotateRefreshToken()` (reissue)로 의도를 표현.
- FK CASCADE는 **DB 레벨에서만 정의** (마이그레이션 SQL의 `ON DELETE CASCADE`). JPA 어노테이션(`@OnDelete`) 불필요 — `members`가 권위 있는 부모이므로 DB가 직접 정리.

### 3.5 Repository

**`SS-Auth/src/main/java/.../auth/repository/UserDeviceRepository.java`**

```java
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByMemberIdAndDeviceId(Long memberId, String deviceId);
    void deleteByMemberIdAndDeviceId(Long memberId, String deviceId);

    default UserDevice getByMemberIdAndDeviceId(Long memberId, String deviceId) {
        return findByMemberIdAndDeviceId(memberId, deviceId)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
    }
}
```

- 네이밍 컨벤션 준수: `getByXxx`(예외) / `findByXxx`(Optional).

### 3.6 Constant Enum

**`SS-Auth/src/main/java/.../auth/constant/DeviceType.java`**

```java
public enum DeviceType { IOS, ANDROID }
```

---

## 4. 인증 흐름 변경

### 4.1 `LoginRequest` DTO

```java
public record LoginRequest(
    @NotNull  SocialType socialType,
    @NotBlank String idToken,
    @NotBlank String fcmToken,
    @NotNull  DeviceType deviceType,
    @NotBlank String deviceId
) {}
```

### 4.2 `JwtTokenProvider` 변경

- `createRefreshToken(Member member, String deviceId)` — `did` claim 추가.
- `parseRefreshToken(String token)` → `RefreshTokenPayload(memberId, deviceId)` 반환. 만료/위변조 시 `CustomException`.
- `parseRefreshTokenSafely(String token)` → `Optional<RefreshTokenPayload>` 반환. 로그아웃에서 만료 토큰도 받아들이기 위해 유지.
- 기존 `getMemberIdFromRefreshToken` / `getMemberIdFromRefreshTokenSafely`는 **제거**. 호출처 일괄 교체.
- Access Token은 변경 없음 (회원만 식별하면 됨).

**`SS-Auth/src/main/java/.../auth/jwt/RefreshTokenPayload.java`**

```java
public record RefreshTokenPayload(Long memberId, String deviceId) {}
```

### 4.3 로그인 흐름

```
AuthService.login(LoginRequest req):
  1. socialId = strategy.validateAndGetSocialId(req.idToken)
  2. (member, isNew) = findOrRegisterMember(socialId, req.socialType)
  3. accessToken  = jwt.createAccessToken(member)
     refreshToken = jwt.createRefreshToken(member, req.deviceId)
  4. userDeviceRepository.findByMemberIdAndDeviceId(member.id, req.deviceId)
       .ifPresentOrElse(
         dev -> dev.renewLogin(req.deviceType, req.fcmToken, refreshToken),
         () -> userDeviceRepository.save(
             UserDevice.register(member.id, req.deviceId, req.deviceType,
                                 req.fcmToken, refreshToken))
       )
  5. return new LoginResponse(member.id, member.nickname,
                              new Tokens(accessToken, refreshToken), isNew)
```

- 전체 트랜잭션 1개(`@Transactional`).
- Race condition: 동일 `(member, deviceId)` 동시 로그인 시 unique 제약 위반 가능. **이번 범위에서는 별도 retry 없이 일반 500 에러로 처리.** 동일 디바이스에서 동시에 두 번 로그인은 현실적으로 발생하기 어려우므로 비용/효익이 맞지 않음.

### 4.4 Reissue 흐름

```
AuthService.reissue(ReissueRequest req):
  1. payload = jwt.parseRefreshToken(req.refreshToken)
  2. device = userDeviceRepository.getByMemberIdAndDeviceId(payload.memberId, payload.deviceId)
  3. if (!device.refreshToken.equals(req.refreshToken)):
       userDeviceRepository.delete(device)   // 탈취 의심 → 강제 로그아웃
       throw INVALID_TOKEN
  4. member = memberRepository.getByMemberId(payload.memberId)
     newAccess  = jwt.createAccessToken(member)
     newRefresh = jwt.createRefreshToken(member, payload.deviceId)
  5. device.rotateRefreshToken(newRefresh)
  6. return new ReissueResponse(new Tokens(newAccess, newRefresh))
```

- Refresh Token Rotation은 그대로 유지.

### 4.5 Logout 흐름

```
AuthService.logout(String refreshToken):
  jwt.parseRefreshTokenSafely(refreshToken)
     .ifPresent(p -> userDeviceRepository
         .deleteByMemberIdAndDeviceId(p.memberId, p.deviceId))
```

- 만료된 refresh token이어도 claim에서 (memberId, deviceId)는 추출 가능 → 정상 정리.
- 다른 디바이스 세션은 영향 없음.

### 4.6 Withdraw 흐름

```
AuthService.withdraw(Long memberId):
  member = memberRepository.findById(memberId).orElse(null)
  if (member != null):
    memberRepository.delete(member)        // CASCADE로 user_devices 자동 삭제
    deleteFirebaseUserSafely(memberId, member.socialId)
```

- 기존의 `refreshTokenRepository.delete(memberId)` 호출 삭제.

---

## 5. 제거되는 코드

| 파일/심볼 | 처리 |
|-----------|------|
| `SS-Auth/repository/RefreshTokenRepository` (Redis) | **삭제** |
| `JwtTokenProvider.getMemberIdFromRefreshToken` | **삭제** (`parseRefreshToken`로 대체) |
| `JwtTokenProvider.getMemberIdFromRefreshTokenSafely` | **삭제** (`parseRefreshTokenSafely`로 대체) |
| `AuthService`의 `RefreshTokenRepository` 주입 | **삭제** |

> Redis 의존성 자체(`spring-boot-starter-data-redis`, `application.yml`의 redis 설정)는 **유지**. 향후 캐싱/레이트리미트 등 다른 용도 여지를 남김. 만약 전체 코드베이스 grep 결과 사용처가 0이면 PR에서 함께 검토.

---

## 6. API 스펙 문서 정합성 보정

`docs/api-specs/01_auth.md` 현재 내용과 코드 간 불일치 일괄 정리:

| 항목 | 현 문서 | 실제 코드 | 조치 |
|------|---------|-----------|------|
| 소셜 플랫폼 필드명 | `socialPlatform` | `socialType` | 문서를 코드에 맞춰 `socialType`으로 수정 |
| 지원 플랫폼 | GOOGLE, APPLE | GOOGLE, APPLE, KAKAO | 문서에 KAKAO 추가 |
| LoginRequest 디바이스 필드 | 이미 명시됨 | 미구현 | 코드 구현(이번 작업) |
| user_devices 컬럼 `refresh_token` | 명시됨 | 미구현 | 코드 구현(이번 작업) |

응답 본문 형식(`memberId`, `nickname`, `tokens`, `isNewMember`)은 변경 없음.

---

## 7. 테스트 전략

### 7.1 신규 테스트

- **`UserDeviceRepositoryTest`** (`@DataJpaTest`)
  - `findByMemberIdAndDeviceId` 존재/부재
  - `deleteByMemberIdAndDeviceId` 정확히 한 row만 삭제
  - `(member_id, device_id)` unique 위반 시 예외
  - members CASCADE 삭제 시 user_devices 동반 삭제

### 7.2 보강할 테스트 (`AuthServiceTest`)

- 로그인 — 신규 디바이스 시나리오: `user_devices`에 새 row, refresh_token 저장.
- 로그인 — 기존 디바이스 재로그인 시나리오: 같은 row의 fcm/refresh/last_login 갱신, row 개수 불변.
- 로그인 — 한 회원이 두 디바이스에서 로그인: row 2개 공존, 각 refresh_token 독립.
- Reissue — 정상 회전: refresh_token DB 값 변경, deviceId 유지.
- Reissue — DB와 불일치하는 토큰: 해당 디바이스 row 삭제 + `INVALID_TOKEN`.
- Reissue — 다른 디바이스 토큰: 다른 디바이스 row는 영향 없음.
- Logout — 해당 디바이스 row만 삭제, 다른 디바이스 row 유지.
- Logout — 만료된 refresh token: 정상 처리(claim에서 memberId/deviceId 추출).
- Withdraw — 모든 디바이스 row CASCADE 삭제, Refresh Token 관련 Redis 호출 없음.

### 7.3 보강할 테스트 (`JwtTokenProviderTest`)

- `createRefreshToken(member, deviceId)` 생성 → `parseRefreshToken` 결과의 deviceId 일치.
- 만료된 refresh token에 대해 `parseRefreshTokenSafely` 가 claim 반환.

### 7.4 영향 받는 기존 테스트

- `AuthServiceTest`의 로그인/logout/reissue/withdraw 모든 케이스 시그니처 변경 영향 → 동시에 수정.

---

## 8. 변경 파일 요약

**신규 (8개)**
- `SS-Auth/src/main/java/.../auth/entity/UserDevice.java`
- `SS-Auth/src/main/java/.../auth/repository/UserDeviceRepository.java`
- `SS-Auth/src/main/java/.../auth/constant/DeviceType.java`
- `SS-Auth/src/main/java/.../auth/jwt/RefreshTokenPayload.java`
- `SS-Auth/src/test/java/.../auth/repository/UserDeviceRepositoryTest.java`
- `SS-Web/src/main/resources/db/migration/V0_0_31__add_user_devices.sql`
- `SS-Auth/src/test/.../auth/jwt/JwtTokenProviderTest.java` (현재 없음 → 신규 작성)

**수정**
- `SS-Auth/src/main/java/.../auth/dto/LoginRequest.java`
- `SS-Auth/src/main/java/.../auth/jwt/JwtTokenProvider.java`
- `SS-Auth/src/main/java/.../auth/service/AuthService.java`
- `SS-Auth/src/test/java/.../auth/service/AuthServiceTest.java`
- `docs/api-specs/01_auth.md`

**삭제**
- `SS-Auth/src/main/java/.../auth/repository/RefreshTokenRepository.java`

---

## 9. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 기존 Redis에 남아있던 refresh_token 무효화 | 배포 직후 기존 사용자 전원 강제 로그아웃 | 의도적 — 안내 공지. 어차피 다중 디바이스 정책이 바뀌므로 마이그레이션 불가. |
| ddl-auto 환경의 `members` 테이블과 마이그레이션 정의 차이 | Flyway baseline 충돌 가능 | `IF NOT EXISTS` 사용. 첫 배포 시 Flyway `baselineOnMigrate=true` 설정 확인. |
| 동일 (member, device) 동시 로그인 시 unique 위반 | 500 응답 | 비현실적 시나리오. 발생 시 일반 에러로 처리, 별도 retry 미구현. |
| FCM 토큰 길이 256 초과 (실제로는 ~152자) | 컬럼 길이 부족 | 일반적으로 152자 수준이라 VARCHAR(255)면 충분. 향후 확장은 별도 마이그레이션. |
