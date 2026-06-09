# 닉네임 중복 확인 및 닉네임 변경 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/auth/check-nickname`, `PATCH /api/auth/nickname` 두 개의 엔드포인트를 TDD로 구현하고 `/api/auth` 화이트리스트를 공개 API(`login`, `reissue`)만 남기도록 정리한다.

**Architecture:** Controller(SS-Web) → AuthService(SS-Auth) → MemberRepository(SS-Member) 계층. 입력 검증은 Bean Validation이 붙은 record DTO로 처리하고, 미인증/중복/형식 오류는 `CustomException`/`GlobalExceptionHandler`가 담당. 인증은 기존 `AuthInterceptor` + `LoginMemberArgumentResolver` 재사용.

**Tech Stack:** Java 21, Spring Boot 4.0.2, Spring Security, Spring Data JPA, Bean Validation(Jakarta), JUnit 5, Mockito, MockMvc(standalone).

**Related spec:** [`docs/superpowers/specs/2026-04-24-nickname-api-design.md`](../specs/2026-04-24-nickname-api-design.md)

---

## File Plan

| 파일 | 구분 | 책임 |
|------|------|------|
| `SS-Web/.../config/WebConfig.java` | 수정 | `AuthInterceptor` 예외 경로를 `login`/`reissue`/`actuator/health`로 축소 |
| `SS-Auth/.../auth/dto/CheckNicknameRequest.java` | 신규 | `@ModelAttribute`로 바인딩될 닉네임 쿼리 DTO (검증 포함) |
| `SS-Auth/.../auth/dto/CheckNicknameResponse.java` | 신규 | `{available: boolean}` 응답 record |
| `SS-Auth/.../auth/dto/UpdateNicknameRequest.java` | 신규 | PATCH 본문 DTO (검증 포함) |
| `SS-Auth/.../auth/dto/UpdateNicknameResponse.java` | 신규 | `{nickname: string}` 응답 record |
| `SS-Auth/.../auth/service/AuthService.java` | 수정 | `checkNickname`, `updateNickname` 메서드 추가 |
| `SS-Web/.../controller/auth/AuthController.java` | 수정 | GET/PATCH 엔드포인트 2개 추가 |
| `SS-Auth/src/test/.../auth/service/AuthServiceTest.java` | 신규 | Service 단위 테스트 |
| `SS-Web/src/test/.../controller/auth/AuthControllerTest.java` | 신규 | Controller 슬라이스 테스트 (MockMvc standalone) |

---

## Task 1: Interceptor 예외 경로 축소

**Why first**: 신규 API의 인증 동작이 이 설정에 의존. 먼저 고쳐두고 빌드를 깨지 않게 유지.

**아키텍처 메모**: Spring Security는 `SecurityUrls.AUTH_WHITELIST`에 `/api/auth/**`를 통째로 permit해 두고 있으며, 실제 JWT 검증은 `AuthInterceptor`가 담당한다. 따라서 `SecurityUrls`는 **변경하지 않고**, `WebConfig`의 인터셉터 `excludePathPatterns`만 축소한다. (만약 `SecurityUrls`에서 `/api/auth/**`를 빼면 Spring Security 필터 체인에 JWT 처리 필터가 없어 정상 요청도 401이 됨 — 이는 별도 이슈로 다룬다.)

**Files:**
- Modify: `SS-Web/src/main/java/com/elipair/spacestudyship/config/WebConfig.java`

- [ ] **Step 1: `WebConfig.addInterceptors` 예외 경로 교체**

`SS-Web/src/main/java/com/elipair/spacestudyship/config/WebConfig.java`의 `addInterceptors` 메서드를 다음처럼 변경:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                    "/api/auth/login",
                    "/api/auth/reissue",
                    "/actuator/health"
            );
}
```

- [ ] **Step 2: 빌드 검증**

Run: `./gradlew build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/config/WebConfig.java
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : refactor : AuthInterceptor 예외 경로를 공개 API로 한정 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 2: DTO 4종 작성

**Why**: Controller/Service 시그니처를 먼저 확정해야 테스트를 TDD로 쓸 수 있음.

**Files:**
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/CheckNicknameRequest.java`
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/CheckNicknameResponse.java`
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/UpdateNicknameRequest.java`
- Create: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/UpdateNicknameResponse.java`

- [ ] **Step 1: `CheckNicknameRequest.java` 생성**

```java
package com.elipair.spacestudyship.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckNicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
        String nickname
) {}
```

- [ ] **Step 2: `CheckNicknameResponse.java` 생성**

```java
package com.elipair.spacestudyship.auth.dto;

public record CheckNicknameResponse(
        boolean available
) {}
```

- [ ] **Step 3: `UpdateNicknameRequest.java` 생성**

```java
package com.elipair.spacestudyship.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
        String nickname
) {}
```

- [ ] **Step 4: `UpdateNicknameResponse.java` 생성**

```java
package com.elipair.spacestudyship.auth.dto;

public record UpdateNicknameResponse(
        String nickname
) {}
```

- [ ] **Step 5: 빌드 검증**

Run: `./gradlew :SS-Auth:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add SS-Auth/src/main/java/com/elipair/spacestudyship/auth/dto/
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : feat : 닉네임 요청/응답 DTO 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 3: `AuthService.checkNickname` 구현 (TDD)

**Files:**
- Create: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java`
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java` (메서드 추가)

- [ ] **Step 1: 실패 테스트 작성 (`AuthServiceTest.java` 신규)**

```java
package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.repository.RefreshTokenRepository;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

    @InjectMocks
    AuthService authService;

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 없으면 available=true")
    void checkNickname_available() {
        // given
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(false);

        // when
        CheckNicknameResponse response = authService.checkNickname(nickname);

        // then
        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 있으면 available=false")
    void checkNickname_notAvailable() {
        // given
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(true);

        // when
        CheckNicknameResponse response = authService.checkNickname(nickname);

        // then
        assertThat(response.available()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest`
Expected: 컴파일 실패 (`authService.checkNickname` 메서드 없음)

- [ ] **Step 3: `AuthService`에 `checkNickname` 구현**

`AuthService.java`의 클래스 본문에 다음 메서드 추가 (기존 `logout` 뒤, 클래스 닫는 중괄호 전):

```java
/**
 * 닉네임 중복 확인
 */
@Transactional(readOnly = true)
public CheckNicknameResponse checkNickname(String nickname) {
    boolean exists = memberRepository.existsByNickname(nickname);
    return new CheckNicknameResponse(!exists);
}
```

파일 상단 import 구문에 필요 시 추가:
```java
import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
```
(이미 `com.elipair.spacestudyship.auth.dto.*` 와일드카드 import가 있으면 생략)

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest`
Expected: 2 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java \
        SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : feat : AuthService.checkNickname 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 4: `AuthService.updateNickname` 구현 (TDD)

**Files:**
- Modify: `SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java` (테스트 추가)
- Modify: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java` (메서드 추가)

- [ ] **Step 1: 실패 테스트 추가**

`AuthServiceTest.java` 상단 import에 추가:

```java
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.entity.Member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
```

**참고**: `MemberRepository.getByMemberId`는 `default` 인터페이스 메서드다. Mockito는 default method body를 실행하지 않고 바로 mock으로 감싸기 때문에, `findById`가 아닌 `getByMemberId`를 직접 stub해야 한다.

클래스 본문 말미(닫는 중괄호 전)에 다음 테스트 3개 추가:

```java
@Test
@DisplayName("updateNickname: 중복 없으면 닉네임 변경 성공")
void updateNickname_success() {
    // given
    Long memberId = 1L;
    String newNickname = "우주탐험가";
    UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
    Member member = Member.builder()
            .id(memberId)
            .socialId("social-id")
            .socialType(SocialType.GOOGLE)
            .nickname("기존닉네임")
            .build();
    given(memberRepository.existsByNickname(newNickname)).willReturn(false);
    given(memberRepository.getByMemberId(memberId)).willReturn(member);

    // when
    UpdateNicknameResponse response = authService.updateNickname(memberId, request);

    // then
    assertThat(response.nickname()).isEqualTo(newNickname);
    assertThat(member.getNickname()).isEqualTo(newNickname);
}

@Test
@DisplayName("updateNickname: 이미 사용 중인 닉네임이면 DUPLICATED_NICKNAME")
void updateNickname_duplicated() {
    // given
    Long memberId = 1L;
    String newNickname = "우주탐험가";
    UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
    given(memberRepository.existsByNickname(newNickname)).willReturn(true);

    // when / then
    assertThatThrownBy(() -> authService.updateNickname(memberId, request))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATED_NICKNAME);
    verify(memberRepository, never()).getByMemberId(any());
}

@Test
@DisplayName("updateNickname: 회원이 없으면 MEMBER_NOT_FOUND")
void updateNickname_memberNotFound() {
    // given
    Long memberId = 1L;
    String newNickname = "우주탐험가";
    UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
    given(memberRepository.existsByNickname(newNickname)).willReturn(false);
    given(memberRepository.getByMemberId(memberId))
            .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // when / then
    assertThatThrownBy(() -> authService.updateNickname(memberId, request))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest`
Expected: 컴파일 실패 (`authService.updateNickname` 메서드 없음)

- [ ] **Step 3: `AuthService`에 `updateNickname` 구현**

`AuthService.java`의 `checkNickname` 바로 뒤에 다음 메서드 추가:

```java
/**
 * 닉네임 변경
 */
@Transactional
public UpdateNicknameResponse updateNickname(Long memberId, UpdateNicknameRequest request) {
    if (memberRepository.existsByNickname(request.nickname())) {
        throw new CustomException(ErrorCode.DUPLICATED_NICKNAME);
    }
    Member member = memberRepository.getByMemberId(memberId);
    member.updateNickname(request.nickname());
    return new UpdateNicknameResponse(member.getNickname());
}
```

필요한 import 추가 (와일드카드로 이미 커버되면 생략):
```java
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
```

- [ ] **Step 4: 테스트 실행 → 전체 통과 확인**

Run: `./gradlew :SS-Auth:test --tests com.elipair.spacestudyship.auth.service.AuthServiceTest`
Expected: 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Auth/src/test/java/com/elipair/spacestudyship/auth/service/AuthServiceTest.java \
        SS-Auth/src/main/java/com/elipair/spacestudyship/auth/service/AuthService.java
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : feat : AuthService.updateNickname 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 5: `AuthController.checkNickname` 엔드포인트 구현 (TDD - 슬라이스)

**Test 전략 메모:** `@WebMvcTest`는 `application.yml`의 `prod` 프로파일과 Firebase/Flyway 등 외부 의존성 때문에 컨텍스트 로드가 까다로워질 수 있음. 대신 `MockMvcBuilders.standaloneSetup`으로 ArgumentResolver와 ControllerAdvice만 직접 주입해 가볍게 구성한다. 이 방식은 `@RequestBody @Valid`, `@ModelAttribute @Valid` 검증이 모두 정상 동작함.

**Files:**
- Create: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java`
- Modify: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java` (GET 엔드포인트 추가)

- [ ] **Step 1: 실패 테스트 작성 (`AuthControllerTest.java` 신규)**

```java
package com.elipair.spacestudyship.controller.auth;

import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMemberArgumentResolver;
import com.elipair.spacestudyship.auth.service.AuthService;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========== GET /api/auth/check-nickname ==========

    @Test
    @DisplayName("checkNickname: 정상 요청이면 200과 available 반환")
    void checkNickname_success() throws Exception {
        // given
        given(authService.checkNickname("우주탐험가"))
                .willReturn(new CheckNicknameResponse(true));

        // when / then
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주탐험가")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("checkNickname: nickname 파라미터가 없으면 400")
    void checkNickname_missingParam() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 1자(길이 미달)이면 400")
    void checkNickname_tooShort() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "가")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 11자(길이 초과)이면 400")
    void checkNickname_tooLong() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "일이삼사오육칠팔구십일")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 특수문자가 포함되면 400")
    void checkNickname_invalidCharacter() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주!탐험")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 인증 정보가 없으면 401")
    void checkNickname_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주탐험가"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.auth.AuthControllerTest`
Expected: 컴파일 실패 또는 404 (GET 엔드포인트 없음)

- [ ] **Step 3: `AuthController`에 GET 엔드포인트 추가**

import 추가:
```java
import com.elipair.spacestudyship.auth.dto.CheckNicknameRequest;
import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import org.springframework.web.bind.annotation.ModelAttribute;
```

클래스 본문(기존 `logout` 아래)에 다음 메서드 추가:

```java
@Operation(summary = "닉네임 중복 확인")
@GetMapping("/check-nickname")
public ResponseEntity<CheckNicknameResponse> checkNickname(
        @AuthMember LoginMember loginMember,
        @Valid @ModelAttribute CheckNicknameRequest request) {
    return ResponseEntity.ok(authService.checkNickname(request.nickname()));
}
```

- [ ] **Step 4: 테스트 실행 → 전체 통과 확인**

Run: `./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.auth.AuthControllerTest`
Expected: 6 tests passed

- [ ] **Step 5: 커밋**

```bash
git add SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java \
        SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : feat : GET /api/auth/check-nickname 엔드포인트 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 6: `AuthController.updateNickname` 엔드포인트 구현 (TDD - 슬라이스)

**Files:**
- Modify: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java` (테스트 추가)
- Modify: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java` (PATCH 엔드포인트 추가)

- [ ] **Step 1: 실패 테스트 추가**

`AuthControllerTest.java` 상단 import에 추가:

```java
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

클래스 본문 필드 영역에 ObjectMapper 추가:

```java
ObjectMapper objectMapper = new ObjectMapper();
```

클래스 본문 말미(닫는 중괄호 전)에 다음 테스트 5개 추가:

```java
// ========== PATCH /api/auth/nickname ==========

@Test
@DisplayName("updateNickname: 정상 요청이면 200과 바뀐 nickname 반환")
void updateNickname_success() throws Exception {
    // given
    UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");
    given(authService.updateNickname(1L, body))
            .willReturn(new UpdateNicknameResponse("우주탐험가"));

    // when / then
    mockMvc.perform(patch("/api/auth/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .requestAttr("loginMember", new LoginMember(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("우주탐험가"));
}

@Test
@DisplayName("updateNickname: 1자 닉네임이면 400")
void updateNickname_tooShort() throws Exception {
    UpdateNicknameRequest body = new UpdateNicknameRequest("가");

    mockMvc.perform(patch("/api/auth/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .requestAttr("loginMember", new LoginMember(1L)))
            .andExpect(status().isBadRequest());
}

@Test
@DisplayName("updateNickname: 특수문자 포함이면 400")
void updateNickname_invalidCharacter() throws Exception {
    UpdateNicknameRequest body = new UpdateNicknameRequest("우주!탐험");

    mockMvc.perform(patch("/api/auth/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .requestAttr("loginMember", new LoginMember(1L)))
            .andExpect(status().isBadRequest());
}

@Test
@DisplayName("updateNickname: 중복 닉네임이면 409")
void updateNickname_duplicated() throws Exception {
    UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");
    given(authService.updateNickname(1L, body))
            .willThrow(new CustomException(ErrorCode.DUPLICATED_NICKNAME));

    mockMvc.perform(patch("/api/auth/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .requestAttr("loginMember", new LoginMember(1L)))
            .andExpect(status().isConflict());
}

@Test
@DisplayName("updateNickname: 인증 정보가 없으면 401")
void updateNickname_unauthenticated() throws Exception {
    UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");

    mockMvc.perform(patch("/api/auth/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.auth.AuthControllerTest`
Expected: 404 또는 405 (PATCH 엔드포인트 없음)

- [ ] **Step 3: `AuthController`에 PATCH 엔드포인트 추가**

클래스 본문(기존 GET checkNickname 아래)에 다음 메서드 추가:

```java
@Operation(summary = "닉네임 변경")
@PatchMapping("/nickname")
public ResponseEntity<UpdateNicknameResponse> updateNickname(
        @AuthMember LoginMember loginMember,
        @RequestBody @Valid UpdateNicknameRequest request) {
    return ResponseEntity.ok(authService.updateNickname(loginMember.memberId(), request));
}
```

- [ ] **Step 4: 테스트 실행 → 전체 통과 확인**

Run: `./gradlew :SS-Web:test --tests com.elipair.spacestudyship.controller.auth.AuthControllerTest`
Expected: 11 tests passed (GET 6개 + PATCH 5개)

- [ ] **Step 5: 커밋**

```bash
git add SS-Web/src/test/java/com/elipair/spacestudyship/controller/auth/AuthControllerTest.java \
        SS-Web/src/main/java/com/elipair/spacestudyship/controller/auth/AuthController.java
git commit -m "닉네임 중복 확인 및 닉네임 변경 API 구현 : feat : PATCH /api/auth/nickname 엔드포인트 추가 https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21"
```

---

## Task 7: 전체 빌드/테스트 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 전체 테스트 통과. 신규 테스트:
- `AuthServiceTest` 5개
- `AuthControllerTest` 11개

기존 `SpaceStudyShipApplicationTests.contextLoads()`는 `prod` 프로파일이 활성이면 환경 변수/외부 의존성 때문에 실패할 수 있음 — 그 경우 **이전과 동일하게 실패**하는 것이지 이번 변경의 회귀가 아닌지 확인한다.

- [ ] **Step 2: 전체 빌드 실행**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`
(만약 기존에 깨져 있던 `SpaceStudyShipApplicationTests` 때문에 실패하면 해당 실패는 이번 변경과 무관함을 주석으로 남기고 태스크 완료 — 이 테스트는 이 이슈 범위가 아님)

- [ ] **Step 3: 수동 검증 (선택)**

브랜치를 `main`과 비교해 진단:

```bash
git diff main --stat
```

Expected: 의도한 파일들만 변경됨 (SecurityUrls, WebConfig, AuthController, AuthService, dto/, 테스트 2개).

---

## 수용 기준 체크리스트

- [ ] `GET /api/auth/check-nickname?nickname=...`이 200 `{available}`를 반환
- [ ] `PATCH /api/auth/nickname`이 200 `{nickname}`를 반환
- [ ] 닉네임 형식 위반(길이/문자) 시 400
- [ ] 중복 닉네임 변경 시도 시 409
- [ ] 인증 없는 요청은 401
- [ ] `/api/auth` 하위의 공개 경로는 `login`, `reissue`로 한정
- [ ] `AuthServiceTest` 5개 모두 통과
- [ ] `AuthControllerTest` 11개 모두 통과
- [ ] 빌드 전체 녹색
