# 회원 탈퇴 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DELETE /api/auth/withdraw` 엔드포인트를 구현하여, 인증된 사용자의 DB row, Redis refresh token, Firebase Authentication 사용자를 모두 삭제한다 (멱등성 유지).

**Architecture:** `AuthController` → `AuthService.withdraw(memberId)` → ① DB 삭제 (`@Transactional` 안) ② Redis 삭제 ③ Firebase 삭제 순서. Redis/Firebase 호출은 try/catch로 격리해 외부 시스템 장애가 DB 롤백을 일으키지 않도록 한다. Firebase 예외(USER_NOT_FOUND 포함 모든 예외)는 로그만 남기고 204 응답.

**Tech Stack:** Spring Boot 4.0.2, Java 21, Gradle 멀티모듈, Spring Data JPA, Redis (refresh token), Firebase Admin SDK 9.4.3, JUnit 5 + Mockito + BDDMockito, MockMvc

**Spec:** [`docs/superpowers/specs/2026-05-11-withdraw-api-design.md`](../specs/2026-05-11-withdraw-api-design.md)
**Issue:** [#22 회원 탈퇴 API 구현](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22)
**Branch:** `20260422_#22_회원_탈퇴_API_구현`

---

## 사전 검증 (작업 시작 전)

- [ ] **현재 브랜치 확인**

```bash
git branch --show-current
```
Expected: `20260422_#22_회원_탈퇴_API_구현`

- [ ] **`.gitignore`에 Firebase 패턴 포함되어 있는지 확인**

```bash
grep -n "firebase" .gitignore
```
Expected:
```
**/firebase/*.json
*-firebase-adminsdk-*.json
```
(없으면 앞 단계에서 빠진 것 — 추가하고 시작)

- [ ] **Firebase Admin SDK 키 파일이 디스크에 존재하는지 + git에 추적 안 되는지 확인**

```bash
ls -la SS-Web/src/main/resources/firebase/
git status --short SS-Web/src/main/resources/firebase/
```
Expected: 파일 존재(`spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json`), `git status`엔 안 나타남.

- [ ] **전체 테스트가 현재 통과하는지 확인 (기준선)**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL (실패하는 테스트가 있다면 본 작업과 별개로 먼저 조사)

---

## Task 1: Firebase Admin SDK 의존성 추가

**Files:**
- Modify: `SS-Auth/build.gradle`

- [ ] **Step 1: `SS-Auth/build.gradle`에 firebase-admin 의존성 추가**

기존 `dependencies` 블록 끝에 한 줄 추가:

```gradle
bootJar {
	enabled = false
}

jar {
	enabled = true
	archiveClassifier = ''
}

dependencies {
	api project(':SS-Common')
	api project(':SS-Member')

	// JWT
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

	// Firebase Admin SDK
	implementation 'com.google.firebase:firebase-admin:9.4.3'
}
```

- [ ] **Step 2: 의존성 다운로드 검증**

```bash
./gradlew :SS-Auth:dependencies --configuration runtimeClasspath | grep firebase
```
Expected:
```
+--- com.google.firebase:firebase-admin:9.4.3
```
(여러 transitive 의존성도 함께 나타남 — `com.google.auth:google-auth-library-oauth2-http`, `com.google.api-client:google-api-client` 등)

만약 버전 충돌이나 다운로드 실패가 나면 9.2.0 / 9.3.0 등 인접 버전으로 조정. 인터넷 차단 환경이면 Gradle 캐시 / 사내 미러 확인.

- [ ] **Step 3: SS-Auth 모듈 컴파일 확인**

```bash
./gradlew :SS-Auth:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add SS-Auth/build.gradle
git commit -m "회원 탈퇴 API 구현 : chore : Firebase Admin SDK 의존성 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 2: `.gitignore` 정리 + Firebase 키 staging 상태 정리

`.gitignore` 변경은 앞 단계에서 이미 디스크 반영됐지만 아직 커밋 전. 키 파일이 다시 staged되어 있지 않은지 재확인 후 커밋한다.

**Files:**
- Modify: `.gitignore` (이미 변경됨, 커밋만)

- [ ] **Step 1: 현재 상태 재확인**

```bash
git status --short
grep -n "firebase" .gitignore
```
Expected:
- `.gitignore`가 `modified`로 잡힘
- Firebase 키 json 파일은 `git status` 결과에 **안 나타나야 함** (gitignored)
- `.gitignore`에 `**/firebase/*.json`과 `*-firebase-adminsdk-*.json` 두 줄이 보여야 함

- [ ] **Step 2: 키 파일이 실수로 staged되지 않았는지 한 번 더 확인**

```bash
git ls-files --error-unmatch SS-Web/src/main/resources/firebase/spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json 2>&1
```
Expected: `error: pathspec '...' did not match any file(s) known to git` — 즉 추적 안 됨.
만약 추적되고 있다면: `git rm --cached <path>` 후 다시 확인.

- [ ] **Step 3: 커밋**

```bash
git add .gitignore
git commit -m "회원 탈퇴 API 구현 : chore : .gitignore에 Firebase Admin SDK 키 패턴 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 3: `application.yml`에 Firebase 키 경로 설정

**Files:**
- Modify: `SS-Web/src/main/resources/application.yml`

- [ ] **Step 1: `application.yml` 끝에 firebase 블록 추가**

기존 파일 끝(`management:` 블록 뒤)에 다음을 추가:

```yaml
# Firebase Admin SDK
firebase:
  admin-sdk-path: classpath:firebase/spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json
```

전체 파일 끝부분이 다음과 같이 되어야 한다:

```yaml
# Actuator (공통)
management:
  endpoints:
    web:
      exposure:
        include: health

# Firebase Admin SDK
firebase:
  admin-sdk-path: classpath:firebase/spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json
```

- [ ] **Step 2: 키 파일이 클래스패스에서 접근 가능한지 확인**

`SS-Web/src/main/resources/firebase/` 디렉토리에 키 파일이 있어야 함. (앞 단계에서 이미 처리되어 있을 것)

```bash
ls -la SS-Web/src/main/resources/firebase/
```
Expected: 키 json 파일 존재.

- [ ] **Step 3: 커밋**

```bash
git add SS-Web/src/main/resources/application.yml
git commit -m "회원 탈퇴 API 구현 : chore : application.yml에 Firebase Admin SDK 키 경로 설정 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 4: `FirebaseConfig` Bean 생성

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/firebase/FirebaseConfig.java`

- [ ] **Step 1: 패키지 디렉토리 생성**

```bash
mkdir -p SS-Auth/src/main/java/com/elipair/spacestudyship/auth/firebase
```

- [ ] **Step 2: `FirebaseConfig.java` 작성**

파일 경로: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/firebase/FirebaseConfig.java`

```java
package com.elipair.spacestudyship.auth.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    @Value("${firebase.admin-sdk-path}")
    private Resource credentialsResource;

    @PostConstruct
    public void initializeFirebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[FirebaseConfig] FirebaseApp 이미 초기화됨, 스킵");
            return;
        }
        try (InputStream stream = credentialsResource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[FirebaseConfig] FirebaseApp 초기화 완료");
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}
```

**왜 이렇게 작성하는가:**
- `@Value`의 `Resource` 타입은 Spring이 `classpath:`/`file:` prefix를 자동 리졸션.
- `@PostConstruct`로 빈 생성 직후 1회 초기화. `FirebaseApp.getApps().isEmpty()` 가드로 중복 초기화 방지.
- `FirebaseAuth`를 `@Bean`으로 노출 → `AuthService`에서 생성자 주입 가능.
- 키 파일 누락/파싱 실패 시 `IOException`이 던져지면서 애플리케이션 기동이 fail-fast.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :SS-Auth:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 애플리케이션 기동 확인 (FirebaseApp 초기화 검증)**

```bash
./gradlew :SS-Web:bootRun --args='--spring.profiles.active=dev' &
```
잠시 대기 후 로그에서 다음을 확인:
```
[FirebaseConfig] FirebaseApp 초기화 완료
```
그리고 `Tomcat started on port 8080` 메시지.

확인 후 종료:
```bash
# 다른 터미널이라면
pkill -f "SS-Web"
# 같은 터미널이면 fg로 가져와서 Ctrl+C
```

**대안 (백그라운드 실행이 부담스러우면):**
이미 `--spring.profiles.active=dev`로 사용자가 jar를 실행 중인 상태가 있다면 jar를 재빌드 후 재기동:

```bash
./gradlew :SS-Web:bootJar
java -jar SS-Web/build/libs/app.jar --spring.profiles.active=dev
```
같은 로그 확인 후 Ctrl+C.

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/firebase/FirebaseConfig.java
git commit -m "회원 탈퇴 API 구현 : feat : FirebaseConfig 빈 추가 (FirebaseApp 초기화) https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 5: `AuthService.withdraw()` — 정상 케이스 (TDD)

**Files:**
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`

- [ ] **Step 1: AuthServiceTest에 FirebaseAuth mock 필드 추가**

`AuthServiceTest.java` 클래스 상단 mock 필드 영역에 다음 한 줄 추가:

```java
    @Mock
    com.google.firebase.auth.FirebaseAuth firebaseAuth;
```

(또는 import 추가:)
```java
import com.google.firebase.auth.FirebaseAuth;
```

추가 후 mock 필드 블록은 다음과 같이 됨:

```java
    @Mock
    MemberRepository memberRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RandomNicknameGenerator randomNicknameGenerator;
    @Mock
    Map<SocialType, SocialLoginStrategy> socialLoginStrategies;
    @Mock
    FirebaseAuth firebaseAuth;
```

- [ ] **Step 2: 실패 테스트 작성 — `withdraw_success`**

`AuthServiceTest.java`의 마지막 `}` 직전(클래스 닫는 중괄호 직전)에 다음 테스트 추가:

```java
    @Test
    @DisplayName("withdraw: Member 존재 시 DB/Redis/Firebase 모두 삭제")
    void withdraw_success() throws Exception {
        // given
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId)
                .socialId(socialId)
                .socialType(SocialType.GOOGLE)
                .nickname("탈퇴할회원")
                .build();
        given(memberRepository.findById(memberId)).willReturn(java.util.Optional.of(member));

        // when
        authService.withdraw(memberId);

        // then
        verify(memberRepository).delete(member);
        verify(refreshTokenRepository).delete(memberId);
        verify(firebaseAuth).deleteUser(socialId);
    }
```

- [ ] **Step 3: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_success"
```
Expected: COMPILATION FAILURE (`withdraw` 메서드 없음) 또는 컴파일은 통과해도 `firebaseAuth` 필드가 service에 주입 안 됨.

- [ ] **Step 4: `AuthService`에 FirebaseAuth 의존성 + `withdraw()` 메서드 추가**

먼저 import 추가:

```java
import com.google.firebase.auth.FirebaseAuth;
```

생성자 주입 필드 영역에 한 줄 추가 (`@RequiredArgsConstructor`가 자동 처리):

```java
    private final FirebaseAuth firebaseAuth;
```

추가 후 필드 블록:

```java
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RandomNicknameGenerator randomNicknameGenerator;
    private final Map<SocialType, SocialLoginStrategy> socialLoginStrategies;
    private final FirebaseAuth firebaseAuth;
```

그리고 클래스 마지막 `}` 직전에 다음 메서드 추가:

```java
    /**
     * 회원 탈퇴 - DB / Redis / Firebase 사용자 삭제
     */
    @Transactional
    public void withdraw(Long memberId) throws com.google.firebase.auth.FirebaseAuthException {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member != null) {
            memberRepository.delete(member);
        }
        refreshTokenRepository.delete(memberId);
        if (member != null) {
            firebaseAuth.deleteUser(member.getSocialId());
        }
    }
```

> **참고:** 이번 단계에선 정상 케이스만 통과시키기 위해 `FirebaseAuthException`을 `throws`로 두고, Task 7에서 try/catch로 격리하면서 signature에서 제거한다.

- [ ] **Step 5: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_success"
```
Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 6: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java
git commit -m "회원 탈퇴 API 구현 : feat : AuthService.withdraw 정상 케이스 구현 (DB/Redis/Firebase 삭제) https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 6: `AuthService.withdraw()` — 멱등성 (Member 없음)

**Files:**
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`

- [ ] **Step 1: 실패 테스트 작성**

`AuthServiceTest.java`의 `withdraw_success` 바로 뒤에 추가:

```java
    @Test
    @DisplayName("withdraw: Member 이미 없으면 멱등 처리 (refresh token만 삭제 시도)")
    void withdraw_alreadyWithdrawn() throws Exception {
        // given
        Long memberId = 1L;
        given(memberRepository.findById(memberId)).willReturn(java.util.Optional.empty());

        // when
        authService.withdraw(memberId);

        // then
        verify(memberRepository, never()).delete(any(Member.class));
        verify(refreshTokenRepository).delete(memberId);
        verify(firebaseAuth, never()).deleteUser(any());
    }
```

- [ ] **Step 2: 테스트 실행 → PASS 확인 (Task 5 구현이 이미 `if (member != null)` 가드를 포함하므로 통과해야 함)**

```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_alreadyWithdrawn"
```
Expected: BUILD SUCCESSFUL, 1 test passed.

**만약 FAIL이라면:** Task 5의 구현이 `orElseThrow`나 다른 형태로 바뀌어 있는 것 — Task 5의 Step 4 구현으로 되돌릴 것.

- [ ] **Step 3: 커밋**

```bash
git add SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java
git commit -m "회원 탈퇴 API 구현 : test : withdraw 멱등성 (Member 없음) 케이스 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 7: `AuthService.withdraw()` — Firebase 예외 처리 + signature 정리

Firebase 호출을 try/catch로 격리하고 service 메서드 signature에서 `throws FirebaseAuthException`을 제거한다.

**Files:**
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java`

- [ ] **Step 1: 실패 테스트 2개 작성 — Firebase USER_NOT_FOUND + 일반 오류**

먼저 import 추가 (테스트 파일 상단):

```java
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.willThrow;
```

(기존에 일부 import는 이미 있을 수 있음 — 중복은 IDE로 정리)

`AuthServiceTest.java`의 `withdraw_alreadyWithdrawn` 바로 뒤에 다음 두 테스트 추가:

```java
    @Test
    @DisplayName("withdraw: Firebase USER_NOT_FOUND 예외는 무시하고 정상 완료")
    void withdraw_firebaseUserNotFound() throws Exception {
        // given
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId)
                .socialId(socialId)
                .socialType(SocialType.GOOGLE)
                .nickname("탈퇴할회원")
                .build();
        given(memberRepository.findById(memberId)).willReturn(java.util.Optional.of(member));

        FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
        given(firebaseEx.getAuthErrorCode()).willReturn(AuthErrorCode.USER_NOT_FOUND);
        willThrow(firebaseEx).given(firebaseAuth).deleteUser(socialId);

        // when (예외 없이 정상 완료되어야 함)
        authService.withdraw(memberId);

        // then
        verify(memberRepository).delete(member);
        verify(refreshTokenRepository).delete(memberId);
        verify(firebaseAuth).deleteUser(socialId);
    }

    @Test
    @DisplayName("withdraw: Firebase 일반 오류도 무시하고 정상 완료 (멱등성 유지)")
    void withdraw_firebaseGenericError() throws Exception {
        // given
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId)
                .socialId(socialId)
                .socialType(SocialType.GOOGLE)
                .nickname("탈퇴할회원")
                .build();
        given(memberRepository.findById(memberId)).willReturn(java.util.Optional.of(member));

        FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
        given(firebaseEx.getAuthErrorCode()).willReturn(AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        given(firebaseEx.getMessage()).willReturn("Firebase 일시 장애");
        willThrow(firebaseEx).given(firebaseAuth).deleteUser(socialId);

        // when (예외 없이 정상 완료되어야 함)
        authService.withdraw(memberId);

        // then
        verify(memberRepository).delete(member);
        verify(refreshTokenRepository).delete(memberId);
        verify(firebaseAuth).deleteUser(socialId);
    }
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_firebaseUserNotFound" \
                       --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_firebaseGenericError"
```
Expected: FAIL. `authService.withdraw(memberId)`가 `FirebaseAuthException`을 던지지만, 테스트는 예외를 catch하지 않고 정상 완료를 기대하고 있어서 실패.

- [ ] **Step 3: `AuthService.withdraw()`에 try/catch 적용 + signature에서 throws 제거**

`AuthService.java`의 `withdraw` 메서드를 다음과 같이 교체:

```java
    /**
     * 회원 탈퇴 - DB / Redis / Firebase 사용자 삭제
     * Firebase 예외는 멱등성 유지를 위해 모두 무시 (로그만 기록).
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member != null) {
            memberRepository.delete(member);
        }
        refreshTokenRepository.delete(memberId);
        if (member != null) {
            deleteFirebaseUserSafely(memberId, member.getSocialId());
        }
    }

    private void deleteFirebaseUserSafely(Long memberId, String socialId) {
        try {
            firebaseAuth.deleteUser(socialId);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                log.warn("[Withdraw] Firebase 사용자 이미 없음 | memberId={}, socialId={}",
                        memberId, socialId);
            } else {
                log.error("[Withdraw] Firebase 사용자 삭제 실패 | memberId={}, socialId={}, error={}",
                        memberId, socialId, e.getMessage());
            }
        }
    }
```

import 추가:

```java
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
```

> **포인트:**
> - public signature에서 `throws FirebaseAuthException` 제거 → Controller에서 신경 안 써도 됨.
> - private helper로 분리해 책임 명확화. `withdraw()`는 흐름, helper는 외부 시스템 예외 정책.

- [ ] **Step 4: 전체 withdraw 테스트 4개 실행 → PASS 확인**

```bash
./gradlew :SS-Auth:test --tests "com.elipair.spacestudyship.auth.service.AuthServiceTest.withdraw_*"
```
Expected: BUILD SUCCESSFUL, 4 tests passed (`withdraw_success`, `withdraw_alreadyWithdrawn`, `withdraw_firebaseUserNotFound`, `withdraw_firebaseGenericError`).

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java \
        SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java
git commit -m "회원 탈퇴 API 구현 : feat : Firebase 예외 처리 격리 (USER_NOT_FOUND/일반 오류 무시) https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 8: `AuthController` — `DELETE /api/auth/withdraw` 엔드포인트 (TDD)

**Files:**
- Modify: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java`
- Modify: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java`

- [ ] **Step 1: 실패 테스트 2개 작성**

`AuthControllerTest.java`의 마지막 테스트 뒤(클래스 닫는 `}` 직전)에 추가. 먼저 import 확인/추가:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.BDDMockito.willDoNothing;
```

테스트 코드:

```java
    // ========== DELETE /api/auth/withdraw ==========

    @Test
    @DisplayName("withdraw: 정상 요청이면 204 응답하고 AuthService.withdraw 호출")
    void withdraw_success() throws Exception {
        // given
        willDoNothing().given(authService).withdraw(1L);

        // when / then
        mockMvc.perform(delete("/api/auth/withdraw")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("withdraw: 인증 정보가 없으면 401")
    void withdraw_unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/auth/withdraw"))
                .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.auth.AuthControllerTest.withdraw_success" \
                      --tests "com.elipair.spacestudyship.controller.auth.AuthControllerTest.withdraw_unauthenticated"
```
Expected: FAIL — 엔드포인트가 없어서 404 (또는 컴파일 오류 가능).

- [ ] **Step 3: `AuthController`에 `withdraw` 엔드포인트 추가**

`AuthController.java` 클래스의 마지막 `}` 직전에 추가:

```java
    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthMember LoginMember loginMember) {
        authService.withdraw(loginMember.memberId());
        return ResponseEntity.noContent().build();
    }
```

기존 import는 이미 모두 있음 (`DeleteMapping`은 `org.springframework.web.bind.annotation.*`로 wildcard import되어 있음).

- [ ] **Step 4: 테스트 실행 → PASS 확인**

```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.auth.AuthControllerTest.withdraw_*"
```
Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java \
        SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java
git commit -m "회원 탈퇴 API 구현 : feat : DELETE /api/auth/withdraw 엔드포인트 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## Task 9: 통합 검증

**Files:** (변경 없음 — 검증만)

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL. 모든 기존 테스트 + 새로 추가한 6개 테스트 통과.

- [ ] **Step 2: 전체 빌드**

```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 애플리케이션 기동 후 엔드포인트 노출 확인**

```bash
./gradlew :SS-Web:bootRun --args='--spring.profiles.active=dev' &
```
로그에서 다음 확인:
- `[FirebaseConfig] FirebaseApp 초기화 완료`
- `Tomcat started on port 8080`

다른 터미널에서 Swagger UI 확인:
```bash
curl -s http://localhost:8080/docs/api-docs | grep -o '"/api/auth/withdraw"' | head -1
```
Expected: `"/api/auth/withdraw"` 한 줄 — Swagger 문서에 엔드포인트가 잡힘.

또는 브라우저로 `http://localhost:8080/docs/swagger` 접속해서 Auth 태그 안에 `DELETE /api/auth/withdraw`가 있는지 확인.

- [ ] **Step 4: 인증 없이 호출 → 401 확인**

```bash
curl -i -X DELETE http://localhost:8080/api/auth/withdraw
```
Expected: `HTTP/1.1 401 Unauthorized` (응답 본문에 `UNAUTHENTICATED_REQUEST` 등)

- [ ] **Step 5: 애플리케이션 종료**

```bash
pkill -f "SS-Web"
```
(또는 bootRun 실행 중인 터미널에서 Ctrl+C)

- [ ] **Step 6: 스펙 문서 상태 업데이트**

`docs/superpowers/specs/2026-05-11-withdraw-api-design.md` 4번째 줄을 수정:

기존:
```
- **Status**: Approved (pending user review)
```

변경 후:
```
- **Status**: Implemented (2026-05-11)
```

- [ ] **Step 7: 최종 커밋**

```bash
git add docs/superpowers/specs/2026-05-11-withdraw-api-design.md \
        docs/superpowers/plans/2026-05-11-withdraw-api.md
git commit -m "회원 탈퇴 API 구현 : docs : 설계/구현 계획 문서 추가 및 상태 업데이트 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22"
```

---

## 작업 완료 체크리스트

- [ ] 모든 9개 Task의 모든 Step 체크박스 완료
- [ ] `./gradlew test` 전체 통과
- [ ] `./gradlew build` 전체 통과
- [ ] `DELETE /api/auth/withdraw` 엔드포인트가 Swagger에 노출됨
- [ ] 인증 없는 호출이 401을 돌려줌
- [ ] Firebase 키 파일이 git 추적 대상이 아님 (`git ls-files`로 확인)
- [ ] 모든 커밋 메시지가 프로젝트 컨벤션 따름 (`{이슈제목} : {type} : {설명} {URL}`)
- [ ] 브랜치는 여전히 `20260422_#22_회원_탈퇴_API_구현`

---

## 변경된 파일 요약

| 파일 | 변경 |
|------|------|
| `.gitignore` | Firebase 키 패턴 추가 |
| `SS-Auth/build.gradle` | `firebase-admin:9.4.3` 의존성 추가 |
| `SS-Web/src/main/resources/application.yml` | `firebase.admin-sdk-path` 설정 추가 |
| `SS-Web/src/main/resources/firebase/spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json` | 디스크에만 (gitignored) |
| `SS-Auth/src/main/java/.../auth/firebase/FirebaseConfig.java` | 신규 — FirebaseApp 초기화 + FirebaseAuth Bean |
| `SS-Auth/src/main/java/.../auth/service/AuthService.java` | `withdraw()` 메서드 + private `deleteFirebaseUserSafely()` 추가 |
| `SS-Web/src/main/java/.../controller/auth/AuthController.java` | `DELETE /api/auth/withdraw` 엔드포인트 추가 |
| `SS-Auth/src/test/java/.../auth/service/AuthServiceTest.java` | 4개 테스트 추가 |
| `SS-Web/src/test/java/.../controller/auth/AuthControllerTest.java` | 2개 테스트 추가 |
| `docs/superpowers/specs/2026-05-11-withdraw-api-design.md` | 신규 / Status 업데이트 |
| `docs/superpowers/plans/2026-05-11-withdraw-api.md` | 신규 (본 문서) |
