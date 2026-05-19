# Firebase ID Token 검증 적용 (소셜 로그인) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/api/auth/login` 의 3개 `SocialLoginStrategy` 구현체(Google/Apple/Kakao) 본문을 Firebase Admin SDK 기반 ID Token 검증으로 교체해, 동일 소셜 계정의 재로그인이 신규 가입으로 처리되는 버그를 제거한다.

**Architecture:** Flutter 가 모든 소셜(Google/Apple/Kakao) 인증을 Firebase Authentication 으로 위임하고 Firebase ID Token 만 백엔드로 보낸다. 백엔드는 소셜 종류 분기 없이 `firebaseAuth.verifyIdToken(idToken).getUid()` 로 사용자별 영구 고유 UID 를 얻어 `socialId` 로 사용한다. `SocialLoginStrategy` 인터페이스/Config 는 그대로 두고 각 구현체 본문만 동일 로직으로 교체한다.

**Tech Stack:** Spring Boot 4.0.2, Java 21, Firebase Admin SDK 9.4.3, JUnit 5, Mockito, AssertJ, BDDMockito, Gradle 멀티모듈.

**Spec:** `docs/superpowers/specs/2026-05-19-firebase-id-token-verification-design.md`

**Commit convention (프로젝트 CLAUDE.md):**
```
{이슈제목} : {type} : {변경사항 설명}
```
이슈 URL 모르므로 생략. 이슈 만들었다면 URL 뒤에 붙이기.

---

## Task 1: `GoogleLoginStrategy` 본문을 Firebase 검증으로 교체

**Files:**
- Test: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategyTest.java` (Create)
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategy.java`

- [ ] **Step 1: 테스트 파일 작성 (실패하는 테스트)**

`SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategyTest.java` 신규 생성:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GoogleLoginStrategyTest {

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    GoogleLoginStrategy strategy;

    @Test
    @DisplayName("validateAndGetSocialId: 유효한 토큰이면 Firebase UID 반환")
    void validateAndGetSocialId_valid() throws FirebaseAuthException {
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("firebase-uid-google-1");
        given(firebaseAuth.verifyIdToken("valid-google-token")).willReturn(token);

        String socialId = strategy.validateAndGetSocialId("valid-google-token");

        assertThat(socialId).isEqualTo("firebase-uid-google-1");
    }

    @Test
    @DisplayName("validateAndGetSocialId: Firebase 검증 실패 시 INVALID_TOKEN")
    void validateAndGetSocialId_invalid() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        given(firebaseAuth.verifyIdToken("invalid-token")).willThrow(ex);

        assertThatThrownBy(() -> strategy.validateAndGetSocialId("invalid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("getSocialType: GOOGLE 반환")
    void getSocialType() {
        assertThat(strategy.getSocialType()).isEqualTo(SocialType.GOOGLE);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.GoogleLoginStrategyTest"
```
Expected: `validateAndGetSocialId_valid` 와 `validateAndGetSocialId_invalid` FAIL (현재 `GoogleLoginStrategy` 가 랜덤값 리턴/예외 없음). `getSocialType` 은 PASS 가능. 핵심은 두 핵심 테스트가 FAIL 하는지.

- [ ] **Step 3: `GoogleLoginStrategy` 본문을 Firebase 검증으로 교체**

`SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategy.java` 전체 교체:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleLoginStrategy implements SocialLoginStrategy {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        try {
            return firebaseAuth.verifyIdToken(socialIdToken).getUid();
        } catch (FirebaseAuthException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.GOOGLE;
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.GoogleLoginStrategyTest"
```
Expected: 3개 테스트 모두 PASS.

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategy.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/GoogleLoginStrategyTest.java
git commit -m "소셜 로그인 Firebase IdToken 검증 적용 : feat : GoogleLoginStrategy 본문 구현 및 단위 테스트 추가"
```

---

## Task 2: `AppleLoginStrategy` 본문을 Firebase 검증으로 교체

**Files:**
- Test: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategyTest.java` (Create)
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategy.java`

- [ ] **Step 1: 테스트 파일 작성 (실패하는 테스트)**

`SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategyTest.java` 신규 생성:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppleLoginStrategyTest {

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    AppleLoginStrategy strategy;

    @Test
    @DisplayName("validateAndGetSocialId: 유효한 토큰이면 Firebase UID 반환")
    void validateAndGetSocialId_valid() throws FirebaseAuthException {
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("firebase-uid-apple-1");
        given(firebaseAuth.verifyIdToken("valid-apple-token")).willReturn(token);

        String socialId = strategy.validateAndGetSocialId("valid-apple-token");

        assertThat(socialId).isEqualTo("firebase-uid-apple-1");
    }

    @Test
    @DisplayName("validateAndGetSocialId: Firebase 검증 실패 시 INVALID_TOKEN")
    void validateAndGetSocialId_invalid() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        given(firebaseAuth.verifyIdToken("invalid-token")).willThrow(ex);

        assertThatThrownBy(() -> strategy.validateAndGetSocialId("invalid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("getSocialType: APPLE 반환")
    void getSocialType() {
        assertThat(strategy.getSocialType()).isEqualTo(SocialType.APPLE);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.AppleLoginStrategyTest"
```
Expected: 핵심 두 테스트 FAIL.

- [ ] **Step 3: `AppleLoginStrategy` 본문을 Firebase 검증으로 교체**

`SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategy.java` 전체 교체:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppleLoginStrategy implements SocialLoginStrategy {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        try {
            return firebaseAuth.verifyIdToken(socialIdToken).getUid();
        } catch (FirebaseAuthException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.APPLE;
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.AppleLoginStrategyTest"
```
Expected: 3개 테스트 모두 PASS.

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategy.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/AppleLoginStrategyTest.java
git commit -m "소셜 로그인 Firebase IdToken 검증 적용 : feat : AppleLoginStrategy 본문 구현 및 단위 테스트 추가"
```

---

## Task 3: `KakaoLoginStrategy` 본문을 Firebase 검증으로 교체

**Files:**
- Test: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategyTest.java` (Create)
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategy.java`

- [ ] **Step 1: 테스트 파일 작성 (실패하는 테스트)**

`SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategyTest.java` 신규 생성:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KakaoLoginStrategyTest {

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    KakaoLoginStrategy strategy;

    @Test
    @DisplayName("validateAndGetSocialId: 유효한 토큰이면 Firebase UID 반환")
    void validateAndGetSocialId_valid() throws FirebaseAuthException {
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("firebase-uid-kakao-1");
        given(firebaseAuth.verifyIdToken("valid-kakao-token")).willReturn(token);

        String socialId = strategy.validateAndGetSocialId("valid-kakao-token");

        assertThat(socialId).isEqualTo("firebase-uid-kakao-1");
    }

    @Test
    @DisplayName("validateAndGetSocialId: Firebase 검증 실패 시 INVALID_TOKEN")
    void validateAndGetSocialId_invalid() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        given(firebaseAuth.verifyIdToken("invalid-token")).willThrow(ex);

        assertThatThrownBy(() -> strategy.validateAndGetSocialId("invalid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("getSocialType: KAKAO 반환")
    void getSocialType() {
        assertThat(strategy.getSocialType()).isEqualTo(SocialType.KAKAO);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.KakaoLoginStrategyTest"
```
Expected: 핵심 두 테스트 FAIL.

- [ ] **Step 3: `KakaoLoginStrategy` 본문을 Firebase 검증으로 교체**

`SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategy.java` 전체 교체:

```java
package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoLoginStrategy implements SocialLoginStrategy {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        try {
            return firebaseAuth.verifyIdToken(socialIdToken).getUid();
        } catch (FirebaseAuthException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.KAKAO;
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run:
```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.social.KakaoLoginStrategyTest"
```
Expected: 3개 테스트 모두 PASS.

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategy.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/KakaoLoginStrategyTest.java
git commit -m "소셜 로그인 Firebase IdToken 검증 적용 : feat : KakaoLoginStrategy 본문 구현 및 단위 테스트 추가"
```

---

## Task 4: 전체 회귀 테스트 + dev DB cleanup + 수동 검증

**Files:** 없음 (코드 변경 없음)

- [ ] **Step 1: 전체 테스트 실행해서 회귀 없음 확인**

Run:
```bash
./gradlew test
```
Expected: 모든 모듈(SS-Auth, SS-Web 등) 테스트 PASS. 특히 `AuthServiceTest`(`SocialLoginStrategy` 를 mock 함) 그대로 통과해야 한다.

회귀가 있으면 STOP — Task 1~3 의 어느 step 에서 시그니처/인터페이스가 어긋났는지 역추적.

- [ ] **Step 2: 전체 빌드 확인 (선택)**

Run:
```bash
./gradlew clean build -x test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: dev DB 의 fake seed row 정리 (수동)**

dev 로컬 PostgreSQL 에서 (앱 떠 있는 상태에서도 OK):

```bash
psql -h localhost -p 5432 -U postgres -d spacestudyship
```

```sql
DELETE FROM user_devices;
DELETE FROM members;
SELECT count(*) FROM members;   -- 0
SELECT count(*) FROM user_devices;   -- 0
\q
```

prod DB(`suh-project.synology.me:5430`) 도 실 사용자가 없다면 동일하게 정리. 사용자 데이터가 있으면 건드리지 말 것.

- [ ] **Step 4: Flutter 로 같은 계정 2 회 로그인 시나리오 검증**

1. Flutter 앱에서 Google 계정 A 로 로그인 → 응답에서 `isNewMember=true` + 백엔드 로그에 `[SignUp] 신규 회원가입 성공 | memberId=1, ...` 출력 확인.
2. 같은 디바이스에서 로그아웃 (`/api/auth/logout`).
3. 같은 Google 계정 A 로 다시 로그인 → 응답에서 `isNewMember=false` + 백엔드 로그에 `[SignUp] ...` **미출력** 확인. `memberId=1` 그대로.
4. dev DB 에서 확인:
   ```sql
   SELECT id, social_id, social_type, nickname FROM members;
   ```
   - row 가 1개만 있어야 하고
   - `social_id` 가 Firebase UID 형식(영숫자 28자 내외, 예: `XyZ12abcdef34567890ghIJklmnOpQ`)이어야 함.
   - 절대 `GOOGLE_SOCIAL_ID_xxxxx` 형태면 안 됨 (그러면 변경 미반영).

- [ ] **Step 5: 마무리 (커밋 없음)**

Task 4 자체에는 코드 변경이 없으므로 커밋 없음. Task 1~3 의 3개 커밋만 브랜치에 남아 있어야 한다.

```bash
git log --oneline -5
```
Expected: 최근 3개 커밋이 Google/Apple/Kakao Strategy 변경.

---

## Notes for the implementing engineer

- **Lombok**: `@RequiredArgsConstructor` 가 `private final` 필드를 받는 생성자를 자동 생성한다. Mockito `@InjectMocks` 와 호환된다.
- **FirebaseAuth 빈**: 이미 `AuthService` 에 주입되어 동작 중이므로 별도 설정 불필요. `firebase.admin-sdk-path` 는 `SS-Web/src/main/resources/application.yml:42` 에 등록됨.
- **`FirebaseToken` mock**: Mockito 5.x 는 final 클래스 mock 가능. 별도 `mockito-inline` 의존성 추가 필요 없음 (현행 의존성으로 동작).
- **`FirebaseAuthException`**: 생성자가 패키지 private 이지만 `mock(FirebaseAuthException.class)` 로 stub 인스턴스 생성 가능. `willThrow(인스턴스)` 사용.
- **테스트 위치**: 신규 디렉토리 `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/social/` 가 자동 생성된다. 기존 `auth/jwt/`, `auth/repository/`, `auth/service/` 와 같은 레벨.
- **메모리 cue**: 메모리 `subagent_driven_commit_pattern` 에 따라 본 프로젝트는 superpowers plan workflow 에서 task 당 자동 commit OK.
- **CLAUDE.md 컨벤션**: `@SuperBuilder` 금지, `@Data` 금지, `@RequiredArgsConstructor` 는 Service/Component 에 허용. 본 변경은 `@RequiredArgsConstructor` 만 추가하므로 컨벤션 위반 없음.
