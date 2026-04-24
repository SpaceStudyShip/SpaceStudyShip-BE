# 닉네임 중복 확인 및 닉네임 변경 API 설계

- **Issue**: [#21 닉네임 중복 확인 및 닉네임 변경 API 구현](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/21)
- **Branch**: `20260422_#21_닉네임_중복_확인_및_닉네임_변경_API_구현`
- **Date**: 2026-04-24
- **Status**: Approved (pending user review)

---

## 1. 목적과 범위

### 목적
회원이 자신의 닉네임을 변경할 수 있도록 2개의 API(중복 확인, 변경)를 제공한다.

### 이번 PR 범위
- `GET /api/auth/check-nickname` (신규)
- `PATCH /api/auth/nickname` (신규)
- `WebConfig.addInterceptors.excludePathPatterns` 축소 (공개 API인 `login`/`reissue`만 제외)
- Service / Controller 테스트 코드 추가
- `ErrorCode`에 필요 시 신규 코드 추가 (현재는 기존 `INVALID_INPUT_VALUE`, `DUPLICATED_NICKNAME` 활용)

### 이번 PR에서 제외 (별도 이슈로 분리)
- 닉네임 금지어(욕설) 필터링
- 전역 에러 응답 포맷 변경 (RFC 7807 Problem Details 등)
- `logout` / `withdraw` 동작 검증 (화이트리스트 변경으로 인한 부수 영향만 고려)

---

## 2. API 계약

### 2-1. 닉네임 중복 확인

```
GET /api/auth/check-nickname?nickname={nickname}
Authorization: Bearer {accessToken}
```

| 항목 | 값 |
|------|----|
| 인증 | 필요 (`@AuthMember LoginMember`) |
| Query Parameter | `nickname` (String, required, 2~10자, `^[가-힣a-zA-Z0-9]+$`) |
| 성공 응답 | `200 OK` `{"available": boolean}` |
| 형식 오류 | `400 INVALID_INPUT_VALUE` |
| 미인증 | `401 UNAUTHENTICATED_REQUEST` |

**동작**: `MemberRepository.existsByNickname(nickname)` 결과의 **부정값**을 `available`로 반환한다. 본인이 사용 중인 닉네임도 `available: false`가 되며, 이는 프론트에서 "본인 닉네임 입력 시 중복확인 버튼 비활성화"로 이미 차단한다.

### 2-2. 닉네임 변경

```
PATCH /api/auth/nickname
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "nickname": "..." }
```

| 항목 | 값 |
|------|----|
| 인증 | 필요 |
| Request Body | `nickname` (String, required, 2~10자, `^[가-힣a-zA-Z0-9]+$`) |
| 성공 응답 | `200 OK` `{"nickname": "..."}` |
| 형식 오류 | `400 INVALID_INPUT_VALUE` |
| 중복 | `409 DUPLICATED_NICKNAME` |
| 미인증 | `401 UNAUTHENTICATED_REQUEST` |
| 회원 없음 | `404 MEMBER_NOT_FOUND` (이론상, 유효 토큰+DB 삭제 동시 발생 시) |

**동작**: 중복 체크 후 `Member.updateNickname()`로 갱신. JPA dirty checking으로 `UPDATE` 쿼리 발생. 토큰 재발급 없음.

### 2-3. 닉네임 형식 규칙

- 길이: **2~10자**
- 허용 문자: 한글, 영문 대소문자, 숫자
- 금지: 공백, 특수문자, 이모지
- 정규식: `^[가-힣a-zA-Z0-9]+$` (+ `@Size(min=2, max=10)`)

---

## 3. 파일 구조

```
SS-Web/
  src/main/java/com/elipair/spacestudyship/
    config/WebConfig.java               [수정] 인터셉터 예외 경로 축소
    controller/auth/AuthController.java [수정] 엔드포인트 2개 추가
  src/test/java/com/elipair/spacestudyship/controller/auth/
    AuthControllerTest.java             [신규] MockMvc 슬라이스

SS-Auth/
  src/main/java/com/elipair/spacestudyship/auth/
    dto/CheckNicknameRequest.java       [신규] record + Bean Validation
    dto/CheckNicknameResponse.java      [신규] record
    dto/UpdateNicknameRequest.java      [신규] record + Bean Validation
    dto/UpdateNicknameResponse.java     [신규] record
    service/AuthService.java            [수정] 메서드 2개 추가
  src/test/java/com/elipair/spacestudyship/auth/
    service/AuthServiceTest.java        [신규] 신규 메서드 커버

SS-Auth (변경 없음)
  constant/SecurityUrls.java            이번 범위에서 변경 없음 (5장 참조)

SS-Member/ (변경 없음)
  entity/Member.java                    기존 updateNickname() 재사용
  repository/MemberRepository.java      기존 existsByNickname() 재사용

SS-Common/ (변경 없음)
  exception/ErrorCode.java              기존 코드로 커버 가능
  exception/GlobalExceptionHandler.java 기존 구조 그대로
```

### 판단 근거

- **Controller는 기존 `AuthController`에 추가**: 스펙상 `/api/auth` 하위 경로이므로 같은 컨트롤러에 두는 것이 일관성 있음
- **Service는 기존 `AuthService`에 추가**: 메서드가 2개뿐이고 Auth 도메인의 프로필 관련 기능. 프로필 도메인이 커지면 그때 `MemberProfileService`로 분리 (YAGNI)
- **DTO는 `SS-Auth/dto/`에 배치**: 기존 `LoginRequest` 등과 동일 위치, CLAUDE.md의 "모듈별 dto/ 폴더" 규칙 준수
- **Entity/Repository 수정 불필요**: `Member.updateNickname()`과 `MemberRepository.existsByNickname()`이 이미 존재

### Flyway 마이그레이션

**필요 없음.** Entity 스키마 변경이 없고 `members` 테이블의 `nickname` 컬럼은 이미 `unique` 제약이 걸려 있다.

---

## 4. 데이터 흐름

### `GET /api/auth/check-nickname?nickname=...`

```
Request → AuthInterceptor (토큰 검증, request.loginMember 세팅)
       → LoginMemberArgumentResolver (LoginMember 주입)
       → AuthController.checkNickname(loginMember, @Valid nickname)
           ↓ @Pattern/@Size 검증 실패 시 MethodArgumentNotValidException
       → AuthService.checkNickname(nickname)
           ↓ memberRepository.existsByNickname(nickname)
       → CheckNicknameResponse(available = !exists)
       → 200 OK
```

### `PATCH /api/auth/nickname`

```
Request → AuthInterceptor → LoginMemberArgumentResolver
       → AuthController.updateNickname(loginMember, @Valid UpdateNicknameRequest)
           ↓ 형식 검증 실패 → 400
       → AuthService.updateNickname(loginMember.memberId(), request) [@Transactional]
           1. existsByNickname(request.nickname())
              → true면 throw CustomException(DUPLICATED_NICKNAME) [409]
           2. getByMemberId(memberId)
              → 없으면 throw CustomException(MEMBER_NOT_FOUND) [404]
           3. member.updateNickname(request.nickname())  // dirty checking
       → UpdateNicknameResponse(member.getNickname())
       → 200 OK
```

### 예외 처리

- Bean Validation 실패 → `GlobalExceptionHandler.handleValidationException`이 `400 INVALID_INPUT_VALUE`로 처리 (필드 에러 메시지 `detail`에 포함)
- `CustomException` → `handleCustomException`이 해당 `ErrorCode`의 `HttpStatus`/`message`로 응답
- 미인증 → `LoginMemberArgumentResolver`가 `UNAUTHENTICATED_REQUEST(401)` throw

### 동시성 / 중복 방지

- `PATCH` 요청에서 `existsByNickname` 체크 후 `update` 사이에 동일 닉네임으로 다른 트랜잭션이 `INSERT`/`UPDATE`할 경우 → **DB의 `nickname` unique 제약이 최종 방어선**
- `@Transactional`로 원자성 확보. unique 제약 위반 시 `DataIntegrityViolationException`이 던져지며, 현재는 `Exception` 핸들러로 500 처리되지만 극히 드문 경합 상황이므로 이번 범위에서는 특별한 처리 없음 (별도 고도화 항목)

---

## 5. 인증 경로 정리

### 아키텍처 이해 (중요)

현재 프로젝트의 인증 구조:
- **Spring Security** (`SecurityConfig`): `SecurityUrls.AUTH_WHITELIST`에 등록된 경로는 `permitAll`. `/api/auth/**`가 통째로 들어있어 이 계층에서는 JWT 검증을 하지 않는다.
- **AuthInterceptor**: `WebConfig.addInterceptors`에서 `/api/**`에 등록되며, `excludePathPatterns` 외의 경로는 `Authorization: Bearer` 토큰을 검증하고 `loginMember` 속성을 세팅한다.

즉, **실질적인 JWT 검증은 `AuthInterceptor`가 담당**한다. Spring Security의 `/api/auth/**` whitelist는 유지해야 한다 (빼면 JWT 처리 필터 부재로 정상 요청도 401이 됨).

### 변경 범위: `WebConfig.addInterceptors.excludePathPatterns`만 조정

#### Before

```java
.excludePathPatterns(
        "/api/auth/**",  // AuthController 전체 제외
        "/actuator/health"
);
```

#### After

```java
.excludePathPatterns(
        "/api/auth/login",
        "/api/auth/reissue",
        "/actuator/health"
);
```

`SecurityUrls.AUTH_WHITELIST`는 이번 범위에서 **변경하지 않는다**. Spring Security 계층 전면 재설계(JWT Security Filter 도입 등)는 별도 이슈에서 다룬다.

### 영향

| API | 기존 동작 | 변경 후 |
|-----|----------|---------|
| `POST /api/auth/login` | 공개 | 공개 (유지) |
| `POST /api/auth/reissue` | 공개 | 공개 (유지) |
| `POST /api/auth/logout` | 공개 (스펙 위반) | 인증 필요 (AuthInterceptor가 검증) |
| `DELETE /api/auth/withdraw` | 미구현 | 미구현 (영향 없음) |
| `GET /api/auth/check-nickname` | 신규 | 인증 필요 |
| `PATCH /api/auth/nickname` | 신규 | 인증 필요 |

---

## 6. 테스트 전략

커버리지 목표: **80%+** (글로벌 `rules/testing.md` 준수)

### 6-1. `AuthServiceTest` (단위 테스트, Mockito)

**checkNickname**
- `existsByNickname`이 `false` → `available=true` 반환
- `existsByNickname`이 `true` → `available=false` 반환

**updateNickname**
- 사용 가능한 닉네임 입력 → `Member.updateNickname` 호출, `UpdateNicknameResponse` 반환
- 이미 존재하는 닉네임 입력 → `CustomException(DUPLICATED_NICKNAME)` throw, 업데이트 호출 안 됨
- `memberId`로 조회 실패 → `CustomException(MEMBER_NOT_FOUND)` throw

### 6-2. `AuthControllerTest` (슬라이스 테스트, MockMvc)

**GET /api/auth/check-nickname**
- 정상 쿼리 + 인증 → 200, `$.available` 필드 검증
- `nickname` 파라미터 누락 → 400
- 1자 / 11자 / 특수문자 / 공백 포함 → 400
- 인증 없음 → 401

**PATCH /api/auth/nickname**
- 정상 요청 + 인증 → 200, `$.nickname` 필드 검증
- 본문 닉네임 형식 위반 → 400
- 중복 닉네임 → 409 (`AuthService` mock이 예외 throw)
- 인증 없음 → 401

### 6-3. Mocking 방침

- Service 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock MemberRepository`
- Controller 테스트: `@WebMvcTest(AuthController.class)` + `AuthService` 모킹, `LoginMemberArgumentResolver` 모킹 또는 request attribute에 `loginMember` 직접 세팅으로 인증 주입
- 모킹 어노테이션은 Spring Boot 4.x 환경에 맞게 `@MockitoBean` 계열 사용 (Spring Framework 6.2+ 표준)

### 6-4. 통합 테스트

이번 범위에서는 제외. 통합 테스트 인프라(`@SpringBootTest` + Testcontainers 또는 H2) 셋업은 별도 이슈에서 프로젝트 전반적으로 구축.

---

## 7. 구현 순서 (TDD)

1. `SecurityUrls.AUTH_WHITELIST` 축소 + 기존 테스트 영향 확인
2. DTO 3개 작성 (`CheckNicknameResponse`, `UpdateNicknameRequest`, `UpdateNicknameResponse`)
3. `AuthServiceTest` 작성 (RED) → `AuthService` 메서드 2개 구현 (GREEN)
4. `AuthControllerTest` 작성 (RED) → `AuthController` 엔드포인트 2개 구현 (GREEN)
5. 빌드 및 테스트 통과 확인
6. 필요 시 리팩터링

---

## 8. 수용 기준 (Acceptance Criteria)

- [ ] `GET /api/auth/check-nickname` 엔드포인트가 스펙대로 동작
- [ ] `PATCH /api/auth/nickname` 엔드포인트가 스펙대로 동작
- [ ] 닉네임 형식 검증(길이, 허용 문자)이 요청 단계에서 동작
- [ ] 중복 닉네임 요청 시 409 응답
- [ ] 인증 없는 요청 시 401 응답
- [ ] `/api/auth` 하위의 공개 API가 `login`, `reissue`로만 한정됨
- [ ] 신규/수정된 코드에 대한 단위/슬라이스 테스트 통과
- [ ] 전체 빌드 및 테스트 녹색
