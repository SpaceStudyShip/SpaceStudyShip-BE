# User Device & FCM Token Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 시 디바이스 정보(`fcmToken`, `deviceType`, `deviceId`)를 받아 `user_devices` 테이블에 저장하고, 디바이스별 Refresh Token 관리로 다중 디바이스 동시 로그인 세션을 지원한다.

**Architecture:** `RefreshTokenRepository`(Redis, 회원당 1개)를 `UserDeviceRepository`(JPA, 디바이스별)로 대체한다. Refresh Token claim에 `did`(deviceId)를 추가하여 토큰만 보고도 어느 디바이스의 세션인지 식별할 수 있게 한다. 모든 인증 흐름(login/reissue/logout/withdraw)이 디바이스 단위로 동작하도록 수정한다.

**Tech Stack:** Spring Boot 4.0.2, Java 21, JPA/Hibernate, **PostgreSQL**, Flyway, jjwt 0.12.6, JUnit 5, Mockito.

**Spec:** [docs/superpowers/specs/2026-05-12-user-device-fcm-token-design.md](../specs/2026-05-12-user-device-fcm-token-design.md)

**커밋 메시지 컨벤션 (CLAUDE.md):**
`디바이스_FCM토큰_저장_기능_추가 : {type} : {설명}` 형식. 각 Task의 커밋 예시는 그 형식을 따른다.

---

## 파일 구조 (전체 변경 사항 매핑)

**신규 생성 (8개):**
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/constant/DeviceType.java` — IOS/ANDROID enum
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/entity/UserDevice.java` — JPA Entity
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepository.java` — Spring Data JPA Repository
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/RefreshTokenPayload.java` — record (memberId, deviceId)
- `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepositoryTest.java`
- `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProviderTest.java`
- `SS-Web/src/main/resources/db/migration/V0_0_31__add_user_devices.sql`

**수정 (5개):**
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/LoginRequest.java`
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProvider.java`
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`
- `docs/api-specs/01_auth.md`

**삭제 (1개):**
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/RefreshTokenRepository.java`

---

## Task 1: DeviceType enum 추가

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/constant/DeviceType.java`

- [ ] **Step 1: Enum 작성**

```java
package com.elipair.spacestudyship.auth.constant;

public enum DeviceType {
    IOS,
    ANDROID
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :SS-Auth:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/constant/DeviceType.java
git commit -m "디바이스_FCM토큰_저장_기능_추가 : feat : DeviceType enum 추가 (IOS, ANDROID)"
```

---

## Task 2: UserDevice Entity 추가

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/entity/UserDevice.java`

- [ ] **Step 1: Entity 작성**

```java
package com.elipair.spacestudyship.auth.entity;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(name = "fcm_token", nullable = false, length = 255)
    private String fcmToken;

    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name = "last_login_at", nullable = false)
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

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :SS-Auth:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/entity/UserDevice.java
git commit -m "디바이스_FCM토큰_저장_기능_추가 : feat : UserDevice Entity 추가"
```

---

## Task 3: UserDeviceRepository + 테스트 (TDD)

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepository.java`
- Test: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepositoryTest.java`

- [ ] **Step 1: Repository 인터페이스 작성 (테스트 컴파일 위해 먼저)**

```java
package com.elipair.spacestudyship.auth.repository;

import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByMemberIdAndDeviceId(Long memberId, String deviceId);

    void deleteByMemberIdAndDeviceId(Long memberId, String deviceId);

    default UserDevice getByMemberIdAndDeviceId(Long memberId, String deviceId) {
        return findByMemberIdAndDeviceId(memberId, deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.elipair.spacestudyship.auth.repository;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(UserDevice.class)
class UserDeviceRepositoryTest {

    @Autowired
    UserDeviceRepository userDeviceRepository;

    @Test
    @DisplayName("findByMemberIdAndDeviceId: 존재하는 row 조회")
    void findByMemberIdAndDeviceId_found() {
        // given
        UserDevice saved = userDeviceRepository.save(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-token-1", "refresh-1"));

        // when
        Optional<UserDevice> found = userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getFcmToken()).isEqualTo("fcm-token-1");
    }

    @Test
    @DisplayName("findByMemberIdAndDeviceId: 다른 deviceId면 Optional.empty")
    void findByMemberIdAndDeviceId_notFound() {
        // given
        userDeviceRepository.save(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-token-1", "refresh-1"));

        // when
        Optional<UserDevice> found = userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-999");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("getByMemberIdAndDeviceId: 없으면 INVALID_TOKEN 예외")
    void getByMemberIdAndDeviceId_throws() {
        assertThatThrownBy(() -> userDeviceRepository.getByMemberIdAndDeviceId(1L, "missing"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("deleteByMemberIdAndDeviceId: 해당 row만 삭제, 다른 row는 유지")
    void deleteByMemberIdAndDeviceId_onlyTargetDeleted() {
        // given
        userDeviceRepository.save(UserDevice.register(
                1L, "device-A", DeviceType.IOS, "fcm-A", "refresh-A"));
        userDeviceRepository.save(UserDevice.register(
                1L, "device-B", DeviceType.ANDROID, "fcm-B", "refresh-B"));

        // when
        userDeviceRepository.deleteByMemberIdAndDeviceId(1L, "device-A");

        // then
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-A")).isEmpty();
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-B")).isPresent();
    }

    @Test
    @DisplayName("(member_id, device_id) 컴포지트 unique 위반 시 DataIntegrityViolationException")
    void uniqueConstraint_violation() {
        // given
        userDeviceRepository.save(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-1", "refresh-1"));

        // when / then
        assertThatThrownBy(() -> {
            userDeviceRepository.saveAndFlush(UserDevice.register(
                    1L, "device-1", DeviceType.ANDROID, "fcm-2", "refresh-2"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 device_id라도 member_id 다르면 별개 row로 공존")
    void sameDeviceIdDifferentMember_coexist() {
        // given / when
        userDeviceRepository.save(UserDevice.register(
                1L, "shared-device", DeviceType.IOS, "fcm-A", "refresh-A"));
        userDeviceRepository.save(UserDevice.register(
                2L, "shared-device", DeviceType.IOS, "fcm-B", "refresh-B"));

        // then
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "shared-device")).isPresent();
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(2L, "shared-device")).isPresent();
    }
}
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Auth:test --tests "*UserDeviceRepositoryTest*"`
Expected: 6개 테스트 모두 실행됨. `@DataJpaTest`가 SS-Auth 모듈에서 H2 인메모리로 스키마 자동 생성. 컴파일 통과되어야 함.

> ⚠️ 만약 `@DataJpaTest`가 ApplicationContext 로딩 실패한다면(다른 모듈의 빈 의존성), `@ContextConfiguration(classes = UserDevice.class)` 또는 `@EntityScan(basePackageClasses = UserDevice.class)` + `@EnableJpaRepositories(basePackageClasses = UserDeviceRepository.class)`를 명시적으로 추가. 이 경우 `@Import(UserDevice.class)`를 다음과 같이 교체:
> ```java
> @DataJpaTest
> @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
> @EntityScan(basePackageClasses = UserDevice.class)
> @EnableJpaRepositories(basePackageClasses = UserDeviceRepository.class)
> ```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :SS-Auth:test --tests "*UserDeviceRepositoryTest*"`
Expected: 6 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepository.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/repository/UserDeviceRepositoryTest.java
git commit -m "디바이스_FCM토큰_저장_기능_추가 : feat : UserDeviceRepository 및 테스트 추가"
```

---

## Task 4: Flyway 마이그레이션 파일

**Files:**
- Create: `SS-Web/src/main/resources/db/migration/V0_0_31__add_user_devices.sql`

- [ ] **Step 1: `version.yml` 현재 버전 확인**

Run: `grep '^version:' version.yml`
Expected: `version: "0.0.30"`

> 다음 PR 머지 시 자동으로 `0.0.31`로 올라간다. 파일명 `V0_0_31__...` 사용.

- [ ] **Step 2: 마이그레이션 SQL 작성 (PostgreSQL 문법)**

```sql
-- members baseline: ddl-auto=update로 이미 생성되어 있을 수 있어 IF NOT EXISTS 사용
CREATE TABLE IF NOT EXISTS members (
    id           BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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

- [ ] **Step 3: 로컬에서 검증 (선택, 환경 있을 시)**

Run: `./gradlew :SS-Web:bootRun --args='--spring.profiles.active=dev'` (Postgres가 떠 있을 때만)
Expected: 애플리케이션 부팅 시 Flyway가 `V0_0_31` 적용. `flyway_schema_history` 테이블에 한 줄 추가.

> 환경 없으면 Step 3 생략. 마이그레이션 적용은 dev/prod 배포 시 자동.

- [ ] **Step 4: 커밋**

```bash
git add SS-Web/src/main/resources/db/migration/V0_0_31__add_user_devices.sql
git commit -m "디바이스_FCM토큰_저장_기능_추가 : chore : V0_0_31 user_devices 마이그레이션 추가"
```

---

## Task 5: RefreshTokenPayload record + JwtTokenProvider 변경 (TDD)

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/RefreshTokenPayload.java`
- Create: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProviderTest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProvider.java`

- [ ] **Step 1: RefreshTokenPayload record 작성**

```java
package com.elipair.spacestudyship.auth.jwt;

public record RefreshTokenPayload(Long memberId, String deviceId) {}
```

- [ ] **Step 2: 실패하는 JwtTokenProviderTest 작성**

```java
package com.elipair.spacestudyship.auth.jwt;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private Member member;

    @BeforeEach
    void setUp() {
        // 32바이트 이상 base64 인코딩된 secret
        String accessSecret = Base64.getEncoder().encodeToString(
                "test-access-secret-with-32-bytes-or-more-length-padding".getBytes());
        String refreshSecret = Base64.getEncoder().encodeToString(
                "test-refresh-secret-with-32-bytes-or-more-length-padding".getBytes());

        JwtProperties props = new JwtProperties(
                new JwtProperties.Token(accessSecret, Duration.ofMinutes(30)),
                new JwtProperties.Token(refreshSecret, Duration.ofDays(14))
        );
        jwtTokenProvider = new JwtTokenProvider(props);

        member = Member.builder()
                .id(42L)
                .socialId("social-id")
                .socialType(SocialType.GOOGLE)
                .nickname("테스터")
                .build();
    }

    @Test
    @DisplayName("createRefreshToken: deviceId claim 포함하여 발급, parseRefreshToken으로 추출 가능")
    void createAndParseRefreshToken() {
        // given
        String deviceId = "device-uuid-123";

        // when
        String token = jwtTokenProvider.createRefreshToken(member, deviceId);
        RefreshTokenPayload payload = jwtTokenProvider.parseRefreshToken(token);

        // then
        assertThat(payload.memberId()).isEqualTo(42L);
        assertThat(payload.deviceId()).isEqualTo(deviceId);
    }

    @Test
    @DisplayName("parseRefreshToken: 위변조된 토큰은 INVALID_TOKEN 예외")
    void parseRefreshToken_invalid() {
        assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken("not-a-jwt"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("parseRefreshTokenSafely: 정상 토큰 → Optional 값 반환")
    void parseRefreshTokenSafely_valid() {
        // given
        String token = jwtTokenProvider.createRefreshToken(member, "device-1");

        // when
        Optional<RefreshTokenPayload> result = jwtTokenProvider.parseRefreshTokenSafely(token);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().memberId()).isEqualTo(42L);
        assertThat(result.get().deviceId()).isEqualTo("device-1");
    }

    @Test
    @DisplayName("parseRefreshTokenSafely: 위변조 토큰 → Optional.empty")
    void parseRefreshTokenSafely_invalid() {
        Optional<RefreshTokenPayload> result = jwtTokenProvider.parseRefreshTokenSafely("garbage");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createAccessToken / getMemberIdFromAccessToken: Access Token 동작 변경 없음")
    void accessTokenStillWorks() {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        Long extracted = jwtTokenProvider.getMemberIdFromAccessToken(accessToken);
        assertThat(extracted).isEqualTo(42L);
    }
}
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Auth:test --tests "*JwtTokenProviderTest*"`
Expected: 컴파일 실패 — `createRefreshToken(Member, String)` 시그니처 없음 / `parseRefreshToken` 없음 / `parseRefreshTokenSafely` 없음.

- [ ] **Step 4: JwtTokenProvider 수정**

`SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProvider.java`의 Refresh Token 섹션을 다음으로 전체 교체:

```java
    // ===== Refresh Token =====

    private static final String CLAIM_DEVICE_ID = "did";

    public String createRefreshToken(Member member, String deviceId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.refresh().expiration().toMillis());

        return Jwts.builder()
                .subject(member.getId().toString())
                .claim(CLAIM_DEVICE_ID, deviceId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(refreshKey)
                .compact();
    }

    public RefreshTokenPayload parseRefreshToken(String refreshToken) {
        Claims claims = getRefreshClaims(refreshToken);
        return toPayload(claims);
    }

    /**
     * 로그아웃 시 사용 - 만료된 토큰에서도 (memberId, deviceId) 추출 시도
     */
    public Optional<RefreshTokenPayload> parseRefreshTokenSafely(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(refreshKey)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            return Optional.of(toPayload(claims));
        } catch (ExpiredJwtException e) {
            return Optional.of(toPayload(e.getClaims()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Claims getRefreshClaims(String refreshToken) {
        try {
            return Jwts.parser()
                    .verifyWith(refreshKey)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    private RefreshTokenPayload toPayload(Claims claims) {
        Long memberId = Long.valueOf(claims.getSubject());
        String deviceId = claims.get(CLAIM_DEVICE_ID, String.class);
        return new RefreshTokenPayload(memberId, deviceId);
    }

    public long getRefreshTokenExpirationMillis() {
        return jwtProperties.refresh().expiration().toMillis();
    }
```

> **삭제되는 메서드:** 기존 `createRefreshToken(Member)`, `getMemberIdFromRefreshToken(String)`, `getMemberIdFromRefreshTokenSafely(String)`. 이 메서드들의 호출처(AuthService)는 Task 7~10에서 동시에 정리되므로 일시적으로 컴파일 에러가 난다.

- [ ] **Step 5: JwtTokenProviderTest만 실행 (전체 빌드는 아직 깨짐)**

Run: `./gradlew :SS-Auth:test --tests "*JwtTokenProviderTest*"`
Expected: 5 tests passed.

> `AuthService`가 아직 옛 시그니처를 호출하고 있어 `compileJava`는 실패하지만, `--tests`로 단일 클래스 실행은 통과해야 한다. 만약 클래스 컴파일 자체가 막혀 테스트도 못 돌리면, Task 5는 Task 7~10과 같은 PR에 묶어 큰 단위로 진행하는 게 안전. 이 경우 Step 6 커밋을 보류하고 Task 7~10 완료 후 한 번에 커밋.

- [ ] **Step 6: 커밋 (Step 5에서 단일 테스트 통과 시)**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/RefreshTokenPayload.java \
        SS-Auth/src/main/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProvider.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/jwt/JwtTokenProviderTest.java
git commit -m "디바이스_FCM토큰_저장_기능_추가 : feat : Refresh Token claim에 deviceId 추가, parseRefreshToken API 신설"
```

---

## Task 6: LoginRequest DTO 확장 + AuthService.login 디바이스 upsert (TDD)

**Files:**
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/LoginRequest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`

- [ ] **Step 1: LoginRequest에 3개 필드 추가**

```java
package com.elipair.spacestudyship.auth.dto;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.member.constant.SocialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청 본문")
public record LoginRequest(
        @Schema(description = "소셜 로그인 플랫폼. 지원: GOOGLE, APPLE, KAKAO.",
                example = "GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "소셜 플랫폼 정보는 필수입니다.") SocialType socialType,

        @Schema(description = "Firebase에서 발급받은 ID Token.",
                example = "eyJhbGciOiJSUzI1NiIs...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "소셜 인증 토큰(ID Token)은 필수입니다.") String idToken,

        @Schema(description = "Firebase Cloud Messaging 디바이스 토큰.",
                example = "dK3mL9xRTp2...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "FCM 토큰은 필수입니다.") String fcmToken,

        @Schema(description = "디바이스 OS 타입.",
                example = "IOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "디바이스 타입은 필수입니다.") DeviceType deviceType,

        @Schema(description = "디바이스 고유 식별자(UUID).",
                example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "디바이스 식별자는 필수입니다.") String deviceId
) {}
```

- [ ] **Step 2: AuthServiceTest 헤더 일괄 정리 — 기존 RefreshTokenRepository 의존을 UserDeviceRepository로 교체**

`AuthServiceTest.java` 상단 필드/import 영역만 우선 다음으로 교체:

```java
import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.auth.dto.LoginRequest;
import com.elipair.spacestudyship.auth.dto.LoginResponse;
import com.elipair.spacestudyship.auth.dto.Tokens;
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.auth.jwt.RefreshTokenPayload;
import com.elipair.spacestudyship.auth.repository.UserDeviceRepository;
// ... (기존 import 유지)

@Mock
UserDeviceRepository userDeviceRepository;   // RefreshTokenRepository 자리 교체
@Mock
JwtTokenProvider jwtTokenProvider;
// ... (나머지 동일)
```

기존 `@Mock RefreshTokenRepository refreshTokenRepository;` 줄을 제거. 기존 `withdraw` 관련 테스트 3개는 `verify(refreshTokenRepository).delete(memberId);` 라인을 일단 **삭제** (다음 Task에서 CASCADE 검증으로 교체).

- [ ] **Step 3: 로그인 — 신규 디바이스 실패 테스트 추가**

`AuthServiceTest`에 추가:

```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@Test
@DisplayName("login: 기존 회원 + 신규 디바이스 → user_devices에 새 row insert, 200 응답")
void login_existingMember_newDevice() {
    // given
    SocialType socialType = SocialType.GOOGLE;
    String idToken = "id-token";
    String fcmToken = "fcm-1";
    DeviceType deviceType = DeviceType.IOS;
    String deviceId = "device-1";
    LoginRequest request = new LoginRequest(socialType, idToken, fcmToken, deviceType, deviceId);

    SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
    given(socialLoginStrategies.get(socialType)).willReturn(strategy);
    given(strategy.validateAndGetSocialId(idToken)).willReturn("social-id-1");

    Member member = Member.builder()
            .id(10L).socialId("social-id-1").socialType(socialType).nickname("기존회원").build();
    given(memberRepository.findBySocialIdAndSocialType("social-id-1", socialType))
            .willReturn(java.util.Optional.of(member));

    given(jwtTokenProvider.createAccessToken(member)).willReturn("access-1");
    given(jwtTokenProvider.createRefreshToken(member, deviceId)).willReturn("refresh-1");
    given(userDeviceRepository.findByMemberIdAndDeviceId(10L, deviceId))
            .willReturn(java.util.Optional.empty());

    // when
    LoginResponse response = authService.login(request);

    // then
    assertThat(response.memberId()).isEqualTo(10L);
    assertThat(response.tokens().accessToken()).isEqualTo("access-1");
    assertThat(response.tokens().refreshToken()).isEqualTo("refresh-1");
    assertThat(response.isNewMember()).isFalse();
    then(userDeviceRepository).should().save(any(UserDevice.class));
}

@Test
@DisplayName("login: 기존 회원 + 기존 디바이스 → 같은 row의 fcm/refresh/last_login 갱신, save() 호출 없음")
void login_existingMember_existingDevice() {
    // given
    SocialType socialType = SocialType.GOOGLE;
    LoginRequest request = new LoginRequest(socialType, "id-token", "fcm-NEW", DeviceType.IOS, "device-1");

    SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
    given(socialLoginStrategies.get(socialType)).willReturn(strategy);
    given(strategy.validateAndGetSocialId("id-token")).willReturn("social-id-1");

    Member member = Member.builder()
            .id(10L).socialId("social-id-1").socialType(socialType).nickname("기존회원").build();
    given(memberRepository.findBySocialIdAndSocialType("social-id-1", socialType))
            .willReturn(java.util.Optional.of(member));

    given(jwtTokenProvider.createAccessToken(member)).willReturn("access-NEW");
    given(jwtTokenProvider.createRefreshToken(member, "device-1")).willReturn("refresh-NEW");

    UserDevice existing = UserDevice.register(10L, "device-1", DeviceType.ANDROID, "fcm-OLD", "refresh-OLD");
    given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
            .willReturn(java.util.Optional.of(existing));

    // when
    authService.login(request);

    // then
    assertThat(existing.getFcmToken()).isEqualTo("fcm-NEW");
    assertThat(existing.getRefreshToken()).isEqualTo("refresh-NEW");
    assertThat(existing.getDeviceType()).isEqualTo(DeviceType.IOS);
    then(userDeviceRepository).should(never()).save(any(UserDevice.class));
}
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Auth:test --tests "*AuthServiceTest.login*"`
Expected: 컴파일 실패 (AuthService가 아직 RefreshTokenRepository 의존, login 시그니처 처리 미흡).

- [ ] **Step 5: AuthService 수정 — login 메서드만**

`AuthService.java`의 필드/생성자 + login 부분을 다음으로 교체:

```java
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.auth.repository.UserDeviceRepository;
// (RefreshTokenRepository import 제거)

private final MemberRepository memberRepository;
private final UserDeviceRepository userDeviceRepository;   // ← 교체
private final JwtTokenProvider jwtTokenProvider;
private final RandomNicknameGenerator randomNicknameGenerator;
private final Map<SocialType, SocialLoginStrategy> socialLoginStrategies;
private final FirebaseAuth firebaseAuth;

@Transactional
public LoginResponse login(LoginRequest request) {
    String socialId = getSocialId(request.socialType(), request.idToken());
    AuthMemberDto authMemberData = findOrRegisterMember(socialId, request.socialType());
    Member member = authMemberData.member();

    String accessToken = jwtTokenProvider.createAccessToken(member);
    String refreshToken = jwtTokenProvider.createRefreshToken(member, request.deviceId());

    upsertUserDevice(member.getId(), request, refreshToken);

    return new LoginResponse(member.getId(), member.getNickname(),
            new Tokens(accessToken, refreshToken), authMemberData.isNewMember());
}

private void upsertUserDevice(Long memberId, LoginRequest request, String refreshToken) {
    userDeviceRepository.findByMemberIdAndDeviceId(memberId, request.deviceId())
            .ifPresentOrElse(
                    device -> device.renewLogin(request.deviceType(), request.fcmToken(), refreshToken),
                    () -> userDeviceRepository.save(UserDevice.register(
                            memberId, request.deviceId(), request.deviceType(),
                            request.fcmToken(), refreshToken))
            );
}
```

기존 `issueTokens(Member)` 메서드는 reissue에서도 사용되므로 **아직 삭제하지 않음** (Task 7에서 처리). 다만 login에서는 이제 안 쓰임.

> `reissue`/`logout`/`withdraw`는 아직 옛 RefreshTokenRepository 호출 중이라 컴파일 실패. 다음 Task에서 차례로 교체.

- [ ] **Step 6: login 관련 테스트만 통과 확인 (전체 빌드는 아직 깨짐)**

> reissue/logout/withdraw가 아직 깨져 있어 전체 컴파일은 실패한다. 이 Task의 커밋은 **다음 Task 7~9를 모두 완료한 뒤** 묶어서 진행한다. Task 6 단독 커밋은 생략.

---

## Task 7: AuthService.reissue 디바이스 단위 회전 (TDD)

**Files:**
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 reissue 테스트 추가**

`AuthServiceTest`에 추가:

```java
@Test
@DisplayName("reissue: DB의 refresh_token과 일치하면 새 토큰 발급 + DB 갱신, deviceId 유지")
void reissue_success() {
    // given
    String oldRefresh = "refresh-OLD";
    ReissueRequest request = new ReissueRequest(oldRefresh);

    given(jwtTokenProvider.parseRefreshToken(oldRefresh))
            .willReturn(new RefreshTokenPayload(10L, "device-1"));
    UserDevice device = UserDevice.register(10L, "device-1", DeviceType.IOS, "fcm", oldRefresh);
    given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
            .willReturn(java.util.Optional.of(device));
    Member member = Member.builder()
            .id(10L).socialId("s").socialType(SocialType.GOOGLE).nickname("닉").build();
    given(memberRepository.getByMemberId(10L)).willReturn(member);
    given(jwtTokenProvider.createAccessToken(member)).willReturn("access-NEW");
    given(jwtTokenProvider.createRefreshToken(member, "device-1")).willReturn("refresh-NEW");

    // when
    ReissueResponse response = authService.reissue(request);

    // then
    assertThat(response.tokens().accessToken()).isEqualTo("access-NEW");
    assertThat(response.tokens().refreshToken()).isEqualTo("refresh-NEW");
    assertThat(device.getRefreshToken()).isEqualTo("refresh-NEW");
}

@Test
@DisplayName("reissue: DB의 refresh_token과 불일치 → 해당 디바이스 row 삭제 + INVALID_TOKEN")
void reissue_tokenMismatch_forceLogout() {
    // given
    String incomingRefresh = "refresh-FORGED";
    ReissueRequest request = new ReissueRequest(incomingRefresh);

    given(jwtTokenProvider.parseRefreshToken(incomingRefresh))
            .willReturn(new RefreshTokenPayload(10L, "device-1"));
    UserDevice device = UserDevice.register(10L, "device-1", DeviceType.IOS, "fcm", "refresh-CURRENT");
    given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
            .willReturn(java.util.Optional.of(device));

    // when / then
    assertThatThrownBy(() -> authService.reissue(request))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    then(userDeviceRepository).should().delete(device);
}

@Test
@DisplayName("reissue: user_devices에 해당 디바이스 row 없으면 INVALID_TOKEN")
void reissue_deviceNotFound() {
    String incoming = "refresh-X";
    ReissueRequest request = new ReissueRequest(incoming);

    given(jwtTokenProvider.parseRefreshToken(incoming))
            .willReturn(new RefreshTokenPayload(10L, "device-gone"));
    given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-gone"))
            .willReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> authService.reissue(request))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
}
```

- [ ] **Step 2: AuthService.reissue 교체**

```java
@Transactional
public ReissueResponse reissue(ReissueRequest request) {
    RefreshTokenPayload payload = jwtTokenProvider.parseRefreshToken(request.refreshToken());

    UserDevice device = userDeviceRepository
            .findByMemberIdAndDeviceId(payload.memberId(), payload.deviceId())
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

    if (!device.getRefreshToken().equals(request.refreshToken())) {
        userDeviceRepository.delete(device);
        log.warn("[Security] Refresh Token 불일치 - 강제 로그아웃 처리 | memberId={}, deviceId={}",
                payload.memberId(), payload.deviceId());
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }

    Member member = memberRepository.getByMemberId(payload.memberId());
    String newAccess = jwtTokenProvider.createAccessToken(member);
    String newRefresh = jwtTokenProvider.createRefreshToken(member, payload.deviceId());

    device.rotateRefreshToken(newRefresh);
    return new ReissueResponse(new Tokens(newAccess, newRefresh));
}
```

기존 `private Tokens issueTokens(Member member)` 헬퍼는 더 이상 호출처가 없으므로 **삭제**.

- [ ] **Step 3: reissue 테스트 통과 확인 (logout/withdraw 아직 깨짐)**

> 전체 컴파일은 여전히 logout/withdraw에서 실패. 다음 Task로 진행 후 통합 검증.

---

## Task 8: AuthService.logout 디바이스 단위 정리 (TDD)

**Files:**
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 logout 테스트 추가**

```java
@Test
@DisplayName("logout: refresh token 파싱 성공 시 해당 (member, device) row 삭제")
void logout_deletesOnlyTargetDevice() {
    // given
    String refreshToken = "refresh-1";
    given(jwtTokenProvider.parseRefreshTokenSafely(refreshToken))
            .willReturn(java.util.Optional.of(new RefreshTokenPayload(10L, "device-1")));

    // when
    authService.logout(refreshToken);

    // then
    then(userDeviceRepository).should().deleteByMemberIdAndDeviceId(10L, "device-1");
}

@Test
@DisplayName("logout: 위변조 등으로 파싱 불가능하면 아무 동작 안 함 (멱등)")
void logout_invalidToken_noop() {
    given(jwtTokenProvider.parseRefreshTokenSafely("garbage"))
            .willReturn(java.util.Optional.empty());

    authService.logout("garbage");

    then(userDeviceRepository).should(never()).deleteByMemberIdAndDeviceId(any(), any());
}
```

- [ ] **Step 2: AuthService.logout 교체**

```java
@Transactional
public void logout(String refreshToken) {
    jwtTokenProvider.parseRefreshTokenSafely(refreshToken)
            .ifPresent(payload -> userDeviceRepository
                    .deleteByMemberIdAndDeviceId(payload.memberId(), payload.deviceId()));
}
```

- [ ] **Step 3: logout 테스트 통과 확인 (withdraw 아직 깨짐)**

> 다음 Task로.

---

## Task 9: AuthService.withdraw CASCADE 의존으로 단순화 (TDD)

**Files:**
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 기존 withdraw 테스트의 RefreshTokenRepository verify 제거 + CASCADE 시나리오 명시**

`AuthServiceTest`의 기존 `withdraw_success` / `withdraw_alreadyWithdrawn` / `withdraw_firebaseUserNotFound` / `withdraw_firebaseGenericError`에서:
- `verify(refreshTokenRepository).delete(memberId);` 줄을 **모두 삭제**
- 대신 `withdraw_success`에 다음 한 줄 추가:
  ```java
  // user_devices는 FK CASCADE로 자동 삭제되므로 AuthService가 직접 호출하지 않는다
  then(userDeviceRepository).shouldHaveNoInteractions();
  ```

- [ ] **Step 2: AuthService.withdraw 교체**

```java
@Transactional
public void withdraw(Long memberId) {
    Member member = memberRepository.findById(memberId).orElse(null);
    if (member != null) {
        memberRepository.delete(member);   // FK ON DELETE CASCADE로 user_devices 자동 삭제
        deleteFirebaseUserSafely(memberId, member.getSocialId());
    }
}
```

> `withdraw_alreadyWithdrawn` 테스트는 member 없음 → delete 호출 없음 + firebase 호출 없음 + userDeviceRepository 무호출. 기존 검증 그대로 유효 (refreshTokenRepository verify 줄만 빠짐).

- [ ] **Step 3: AuthService import에서 RefreshTokenRepository 제거 + Map import 정리**

`AuthService.java` 상단에서 다음 import 제거:
- `import com.elipair.spacestudyship.auth.repository.RefreshTokenRepository;`

`AuthService` 클래스에서 다음 필드 제거:
- `private final RefreshTokenRepository refreshTokenRepository;`

- [ ] **Step 4: 전체 AuthService 테스트 실행 → 통과 확인**

Run: `./gradlew :SS-Auth:test --tests "*AuthServiceTest*"`
Expected: 모든 테스트 통과.

- [ ] **Step 5: Task 6~9 통합 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/LoginRequest.java \
        SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java
git commit -m "디바이스_FCM토큰_저장_기능_추가 : feat : login/reissue/logout/withdraw를 디바이스 단위로 처리"
```

---

## Task 10: RefreshTokenRepository(Redis) 제거

**Files:**
- Delete: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/RefreshTokenRepository.java`

- [ ] **Step 1: 호출처 0건 확인 (이미 모두 교체되었는지)**

Run: `grep -r "RefreshTokenRepository" /Users/luca/workspace/Java_Spring/space_study_ship --include="*.java"`
Expected: 단 한 줄도 출력되지 않음 (파일 자체 외에는). 만약 잔여 호출이 있으면 그 파일을 먼저 정리.

- [ ] **Step 2: 파일 삭제**

```bash
rm SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/RefreshTokenRepository.java
```

- [ ] **Step 3: 전체 빌드 + 모든 테스트 통과 확인**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. 모든 테스트 통과. 컴파일 경고/에러 없음.

- [ ] **Step 4: 커밋**

```bash
git add -A SS-Auth/src/main/java/com/elipair/spacestudyship/auth/repository/
git commit -m "디바이스_FCM토큰_저장_기능_추가 : refactor : RefreshTokenRepository(Redis 기반) 제거"
```

---

## Task 11: API 스펙 문서 정합성 보정

**Files:**
- Modify: `docs/api-specs/01_auth.md`

- [ ] **Step 1: `socialPlatform` → `socialType` 일괄 치환, KAKAO 추가**

`docs/api-specs/01_auth.md`의 "1. 소셜 로그인" 섹션에서 Request Body 표와 JSON 예시 모두 다음 변경:
- `socialPlatform` → `socialType`
- 설명: `"GOOGLE"`, `"APPLE"` → `"GOOGLE"`, `"APPLE"`, `"KAKAO"`
- "Error" 표의 `UNSUPPORTED_PLATFORM` → `UNSUPPORTED_SOCIAL_TYPE` (실제 ErrorCode와 일치)

```diff
- | `socialPlatform` | String | O | 소셜 로그인 플랫폼 | `"GOOGLE"`, `"APPLE"` |
+ | `socialType` | String | O | 소셜 로그인 플랫폼 | `"GOOGLE"`, `"APPLE"`, `"KAKAO"` |
...
- "socialPlatform": "GOOGLE",
+ "socialType": "GOOGLE",
...
- | 400 | `UNSUPPORTED_PLATFORM` | socialPlatform이 GOOGLE/APPLE이 아닌 경우 |
+ | 400 | `UNSUPPORTED_SOCIAL_TYPE` | socialType이 GOOGLE/APPLE/KAKAO가 아닌 경우 |
```

- [ ] **Step 2: "서버 처리 로직" 문구 보정 (디바이스별 Refresh Token 명시)**

기존:
```
5. Refresh Token을 DB에 저장 (디바이스별)
```
→ 그대로 유지. 이제 실제 구현이 일치함.

- [ ] **Step 3: `user_devices` DB 참고 표 — `created_at`, `updated_at` 컬럼 추가 (실제 스키마와 일치)**

```diff
  | `last_login_at` | TIMESTAMP | 마지막 로그인 |
+ | `created_at` | TIMESTAMP | 생성 시각 |
+ | `updated_at` | TIMESTAMP | 수정 시각 |
```

추가로 unique 제약 명시 줄을 표 아래에 추가:
```markdown
> Unique 제약: `(member_id, device_id)` 컴포지트. 같은 디바이스를 다른 회원이 쓰는 경우는 별개 row.
> FK: `member_id` → `members.id`, `ON DELETE CASCADE` (회원 탈퇴 시 자동 삭제).
```

- [ ] **Step 4: 커밋**

```bash
git add docs/api-specs/01_auth.md
git commit -m "디바이스_FCM토큰_저장_기능_추가 : docs : 01_auth.md를 실제 구현(socialType, KAKAO, user_devices)에 맞춰 정합성 보정"
```

---

## Task 12: 최종 검증

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. 모든 테스트 통과.

- [ ] **Step 2: 컴파일 잔여 import / 미사용 클래스 검색**

Run: `grep -rn "refreshTokenRepository\|RefreshTokenRepository" /Users/luca/workspace/Java_Spring/space_study_ship --include="*.java"`
Expected: 출력 없음.

Run: `grep -rn "getMemberIdFromRefreshToken" /Users/luca/workspace/Java_Spring/space_study_ship --include="*.java"`
Expected: 출력 없음.

- [ ] **Step 3: 마이그레이션 파일 한 개인지 확인 (CLAUDE.md 규칙: 한 version당 한 파일)**

Run: `ls SS-Web/src/main/resources/db/migration/`
Expected: `.gitkeep`, `V0_0_31__add_user_devices.sql` 두 개만.

- [ ] **Step 4: PR 생성 가능 상태 — 마지막 확인**

수동 점검:
1. `LoginRequest`에 fcmToken, deviceType, deviceId 3개 필드 포함되어 있는가?
2. `UserDevice` Entity와 마이그레이션 SQL의 컬럼/제약이 정확히 일치하는가?
3. `AuthServiceTest` 9개 이상 (기존 + 신규 login 2개 + reissue 3개 + logout 2개) 통과하는가?
4. `JwtTokenProviderTest` 5개 통과하는가?
5. `UserDeviceRepositoryTest` 6개 통과하는가?
6. `docs/api-specs/01_auth.md`에 `socialType`/`KAKAO`/`UNSUPPORTED_SOCIAL_TYPE` 적용되었는가?
7. `RefreshTokenRepository.java`가 삭제되었고 어떤 코드도 더 이상 참조하지 않는가?

---

## 부록: 디버깅 가이드

**`@DataJpaTest` ApplicationContext 로딩 실패 시:**
- 멀티모듈 환경에서 `@DataJpaTest`는 자기 모듈 내 Entity만 스캔. `UserDevice`만 필요하므로 OK. 만약 `Member` Entity 매핑까지 요구하면(다른 모듈), `@EntityScan(basePackages = "com.elipair.spacestudyship")`로 범위 확장.
- `BaseTimeEntity`는 `@MappedSuperclass`로 상속되므로 별도 스캔 불필요.

**Flyway baseline 충돌 시 (배포):**
- `application.yml`에 `spring.flyway.baseline-on-migrate: true`로 이미 설정되어 있어 첫 마이그레이션 실행 가능. validate-on-migrate=false라 기존 ddl-auto 스키마와의 미세한 차이도 통과.

**Hibernate가 만든 `members` 컬럼 길이/타입이 마이그레이션 SQL과 다를 경우:**
- `CREATE TABLE IF NOT EXISTS`라 기존 테이블은 그대로 유지됨. 향후 컬럼 동기화는 별도 마이그레이션으로 처리.

**PostgreSQL 인덱스 IF NOT EXISTS 미지원 버전:**
- PostgreSQL 9.5+에서 지원. 더 낮은 버전을 쓰면 인덱스 줄을 단순 `CREATE INDEX idx_user_devices_member ON user_devices(member_id);`로 두고 첫 배포 외 환경에서는 `DROP INDEX IF EXISTS` 선행. 현재 프로젝트 PostgreSQL 버전은 9.5+ 가정.
