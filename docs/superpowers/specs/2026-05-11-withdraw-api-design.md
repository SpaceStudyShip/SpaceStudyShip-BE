# 회원 탈퇴 API 설계

- **Issue**: [#22 회원 탈퇴 API 구현](https://github.com/SpaceStudyShip/SpaceStudyShip-BE/issues/22)
- **Branch**: `20260422_#22_회원_탈퇴_API_구현`
- **Date**: 2026-05-11
- **Status**: Implemented (2026-05-11)

---

## 1. 목적과 범위

### 목적
인증된 사용자가 자신의 계정과 관련 데이터를 영구 삭제할 수 있도록 `DELETE /api/auth/withdraw` 엔드포인트를 제공한다. 우리 측 데이터(DB row, Redis refresh token)와 함께 **Firebase Authentication 사용자**도 삭제한다.

### 이번 PR 범위
1. **회원 탈퇴 API 구현**
   - `DELETE /api/auth/withdraw` (신규)
   - `AuthService.withdraw(memberId)` 메서드 추가
2. **삭제 대상 데이터**
   - `members` 테이블의 해당 row
   - Redis `refresh_token:{memberId}` 키
   - **Firebase Authentication 사용자** (`socialId` = Firebase UID로 가정)
3. **Firebase Admin SDK 연동 인프라**
   - `SS-Auth/build.gradle`에 `firebase-admin` 의존성 추가
   - `FirebaseConfig` (FirebaseApp 초기화 빈)
   - `application.yml`에 키 파일 경로 설정
   - `.gitignore`에 Firebase 키 패턴 추가 (이미 반영됨)
4. **테스트**
   - Service / Controller 테스트 코드 추가
   - `FirebaseAuth` mock 처리

### 이번 PR에서 제외 (별도 이슈로 분리)
- **LoginStrategy 실제 구현** — 현재 `GoogleLoginStrategy`/`AppleLoginStrategy`/`KakaoLoginStrategy`가 모두 TODO 스텁이고, 가짜 `socialId`를 발급한다. 이를 실제 Firebase ID Token 검증으로 바꾸는 작업은 그 자체로 큰 작업이라 별도 이슈로 분리. 본 PR에선 **Firebase Admin SDK 초기화와 `deleteUser` 호출만** 포함.
- **Apple `Sign in with Apple` revoke token 처리** — App Store 심사 요구사항이지만 Firebase 연동과는 별개. LoginStrategy 실제 구현 이슈에서 함께 처리.
- **타 도메인 cascade 삭제** — Todo / Timer / Fuel / Exploration / Badge / Friends 등 `docs/api-specs/01_auth.md`에 명시된 도메인은 현재 미구현. 각 도메인이 추가되는 PR에서 자기 데이터 삭제 로직을 함께 추가하는 방식으로 확장.
- **Soft delete / grace period** — 현재 Member 엔티티에 `deletedAt` 등 인프라 없음. 운영 정책상 필요 시 별도 이슈.
- **FK 제약 / `ON DELETE CASCADE` 전략** — 참조 테이블 자체가 아직 없으므로 결정 보류.
- **Flyway 마이그레이션** — 본 작업은 스키마 변경 없음. 마이그레이션 파일 추가 안 함.

### 기존 가짜 socialId 데이터에 대한 처리
LoginStrategy가 TODO 스텁인 동안 가입된 회원의 `socialId`는 Firebase에 존재하지 않는 가짜값(`"GOOGLE_SOCIAL_ID_12345"` 등)이다. 이런 회원이 탈퇴를 호출하면 `FirebaseAuth.deleteUser()`가 `FirebaseAuthException(USER_NOT_FOUND)`를 던진다. 이 예외는 **로그 경고만 남기고 무시**한다 (§5 참조). 멱등성 유지.

---

## 2. API 계약

```http
DELETE /api/auth/withdraw
Authorization: Bearer {accessToken}
```

| 항목 | 값 |
|------|----|
| 인증 | 필요 (`@AuthMember LoginMember`) |
| Request Body | 없음 |
| 성공 응답 | `204 No Content` (응답 본문 없음) |
| 미인증 | `401 UNAUTHENTICATED_REQUEST` |
| 이미 탈퇴됨 | `204 No Content` (멱등 — 별도 에러 응답 없음) |

### 멱등성

DELETE 메서드의 HTTP 의미를 따라 멱등으로 설계한다.
- 동일한 토큰으로 두 번 호출되거나, 다른 디바이스에서 먼저 탈퇴된 후 호출되어도 결과 상태는 동일하므로 `204`로 응답한다.
- 클라이언트의 재시도(네트워크 불안 등)가 안전하다.
- Firebase 측에서 이미 사용자가 삭제된 경우(`USER_NOT_FOUND`)도 멱등으로 처리한다.

---

## 3. 컴포넌트 변경 사항

```
SS-Auth/
├── build.gradle                              ← firebase-admin 의존성 추가
├── service/AuthService.java                  ← withdraw(Long memberId) 추가, FirebaseAuth 주입
└── firebase/FirebaseConfig.java              ← 신규 (FirebaseApp 초기화 빈)

SS-Web/
├── controller/auth/AuthController.java       ← DELETE /api/auth/withdraw 추가
├── src/main/resources/application.yml        ← firebase.admin-sdk-path 설정 추가
└── src/main/resources/firebase/
    └── spacestudyship-firebase-adminsdk-...json  ← 키 파일 (gitignored)

.gitignore                                    ← Firebase 키 패턴 추가 (이미 반영)

SS-Auth (test)/
└── service/AuthServiceTest.java              ← withdraw 케이스 추가

SS-Web (test)/
└── controller/auth/AuthControllerTest.java   ← withdraw 케이스 추가
```

**추가 없음:**
- 신규 DTO (Request/Response 본문 없음)
- 신규 ErrorCode
- 신규 엔티티 / 마이그레이션

### 3-1. `firebase-admin` 의존성

`SS-Auth/build.gradle`에 추가:
```gradle
implementation 'com.google.firebase:firebase-admin:9.x.x'  // 최신 안정 버전 사용
```

### 3-2. `FirebaseConfig`

위치: `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/firebase/FirebaseConfig.java`

역할:
- `@Configuration` 빈으로 애플리케이션 시작 시 `FirebaseApp.initializeApp()` 1회 호출
- 키 파일 경로는 `@Value("${firebase.admin-sdk-path}")`로 주입 (classpath 리소스)
- `FirebaseAuth` 인스턴스를 `@Bean`으로 노출 → `AuthService`에 주입 가능

### 3-3. `application.yml` 추가

```yaml
firebase:
  admin-sdk-path: classpath:firebase/spacestudyship-firebase-adminsdk-fbsvc-7e86c5c253.json
```

- 키 파일이 클래스패스에 없는 경우(예: CI에서 빌드만 할 때) `FirebaseApp` 초기화가 실패하면 애플리케이션 기동 자체가 실패한다.
- CI/CD에서 키 없이 빌드하려면 `application-{profile}.yml`에서 profile별로 경로를 다르게 두거나, `@Profile` 분기로 빈 등록을 막는 방식 필요 — 본 PR 범위 안: 일단 단일 경로로 시작, 운영상 필요해지면 후속 처리.

---

## 4. 데이터 흐름

```
Client
  │  DELETE /api/auth/withdraw  (Authorization: Bearer ...)
  ▼
AuthInterceptor (토큰 검증 → memberId 추출)
  ▼
AuthController.withdraw(@AuthMember LoginMember loginMember)
  ▼
AuthService.withdraw(loginMember.memberId())   @Transactional
  │
  ├─ 1. Member 조회 (socialId가 Firebase UID — 다음 단계용)
  │     Optional<Member> member = memberRepository.findById(memberId)
  │
  ├─ 2. (member가 있으면) memberRepository.delete(member)
  │       없으면 NoOp → 멱등
  │
  ├─ 3. refreshTokenRepository.delete(memberId)
  │       Redis: 키 없어도 silent → 멱등
  │
  └─ 4. (member가 있었으면) firebaseAuth.deleteUser(member.getSocialId())
        try / catch FirebaseAuthException
          ├─ USER_NOT_FOUND → log.warn 후 무시 (멱등)
          └─ 그 외 → log.error 후 무시 (DB는 이미 정리됨, 응답은 204)
  ▼
ResponseEntity.noContent().build()  (204)
```

### 삭제 순서: **DB → Redis → Firebase**

| 시나리오 | 결과 |
|---------|------|
| 모두 성공 | 완전 삭제 (정상) |
| DB 실패 | 트랜잭션 롤백 → Redis/Firebase 호출 안 됨 → 5xx 응답, 클라이언트 재시도 |
| DB 성공 + Redis 실패 | DB는 커밋됨. 토큰은 TTL 만료. `log.warn` 후 다음 단계 진행 |
| DB 성공 + Firebase 실패 | DB/Redis 정리됨. Firebase 유저만 잔존. `log.error` 후 204 응답 (운영자 수동 정리 또는 후속 retry 큐 — 본 PR 범위 외) |

**근거:**
- DB부터 정리하는 이유: 트랜잭션 보장이 가장 강하고, 우리 도메인의 진실 원천이므로 여기 정리되면 사용자 입장에선 "탈퇴 완료".
- Firebase가 가장 외부 시스템이므로 마지막. 실패 시 우리 측 정리는 이미 되어 있어 사용자에겐 "탈퇴됨"으로 보임.
- `@Transactional` 경계 안에 Redis/Firebase가 들어가면 안 됨 — 외부 호출 실패가 DB 롤백을 일으키면 정합성이 더 망가짐. Redis/Firebase 호출은 트랜잭션 커밋 이후 영역으로 두거나, 같은 메서드 내에 두되 try/catch로 격리.

**트랜잭션 경계 구현 노트:** 가장 단순한 방식은 `AuthService.withdraw()` 메서드 자체를 `@Transactional`로 두되, Redis/Firebase 호출을 try/catch로 감싸서 그 예외가 트랜잭션 밖으로 새지 않게 하는 것이다. Redis/Firebase는 DB와 별도 시스템이라 사실상 트랜잭션 보호 대상이 아님을 명시.

---

## 5. 에러 처리

| 상황 | HTTP | code | 비고 |
|------|------|------|------|
| 정상 탈퇴 | 204 | - | |
| 이미 탈퇴 (Member row 없음) | 204 | - | `findById().ifPresent(::delete)` 패턴으로 NoOp. Firebase 호출도 스킵 |
| 토큰 없음 / 만료 | 401 | `UNAUTHENTICATED_REQUEST` 등 | 인터셉터 처리 (기존 패턴) |
| Redis 통신 실패 | 204 | - | DB는 이미 커밋, 토큰은 TTL 만료. `log.warn` 기록. 다음 단계 진행 |
| Firebase `USER_NOT_FOUND` | 204 | - | 가짜 socialId(LoginStrategy 스텁) 또는 다른 디바이스 선행 탈퇴. `log.warn` 후 무시 |
| Firebase 기타 통신/인증 실패 | 204 | - | DB/Redis는 이미 정리됨. `log.error` 후 무시. 응답은 멱등성 유지 위해 204 |
| FirebaseApp 초기화 실패 (앱 기동 시) | 앱 기동 실패 | - | 키 파일 누락/파싱 실패 시 즉시 발견되도록 fail-fast |
| DB 통신 실패 | 500 | `INTERNAL_SERVER_ERROR` | `GlobalExceptionHandler` 위임 |

**새 ErrorCode 추가 없음.**

### Firebase 예외 무시 정책의 근거
- 사용자 관점에서 "탈퇴"의 본질은 "우리 서비스에서 내 데이터가 사라지는 것"이다. Firebase 측 정리는 부수 효과.
- Firebase 일시 장애로 탈퇴 자체가 실패하면 사용자 경험이 나빠지고, 재시도하면 우리 DB는 이미 비어있어 결과가 동일하므로 멱등성을 깨지 않는 게 낫다.
- 잔존 Firebase 유저는 운영 모니터링으로 별도 정리. 향후 retry 큐/배치로 자동화 가능 (별도 이슈).

---

## 6. 테스트 전략

### 6-1. Unit Test — `AuthServiceTest`

| 케이스 | 검증 내용 |
|--------|----------|
| `withdraw_success` | Member 존재 시: `memberRepository.delete(member)` 1회, `refreshTokenRepository.delete(memberId)` 1회, `firebaseAuth.deleteUser(socialId)` 1회 호출 |
| `withdraw_alreadyWithdrawn` | `findById`가 `Optional.empty()` 반환 시 `delete(...)` 및 `firebaseAuth.deleteUser(...)`는 호출되지 않음. `refreshTokenRepository.delete`만 호출됨. 예외 없이 통과 (멱등) |
| `withdraw_firebaseUserNotFound` | `firebaseAuth.deleteUser()`가 `FirebaseAuthException(USER_NOT_FOUND)` 던져도 메서드는 정상 완료. 우리 측 정리는 이미 됨 |
| `withdraw_firebaseGenericError` | `firebaseAuth.deleteUser()`가 일반 `FirebaseAuthException` 던져도 정상 완료. log.error만 호출 (mock으로 검증) |

`@ExtendWith(MockitoExtension.class)` + `@Mock FirebaseAuth firebaseAuth` 추가. `FirebaseAuthException`은 final 클래스가 아니므로 Mockito로 mock 가능.

### 6-2. Controller Test — `AuthControllerTest`

| 케이스 | 검증 내용 |
|--------|----------|
| `withdraw_success` | 204 응답, `authService.withdraw(1L)` 호출 검증 |
| `withdraw_unauthenticated` | Authorization 헤더 없으면 401 |

`MockMvcBuilders.standaloneSetup` + `LoginMemberArgumentResolver` 기존 패턴 그대로 사용.

### 6-3. 통합 테스트
현재 다른 API들도 통합 테스트를 두지 않는 컨벤션이라 일관성을 위해 추가하지 않는다. `FirebaseConfig` 빈 초기화 검증도 통합 테스트 추가하지 않음 — 운영 환경에서 fail-fast로 발견되는 게 충분.

---

## 7. 향후 확장 포인트 (이번 PR 범위 아님)

1. **LoginStrategy 실제 Firebase 검증 구현**
   - `GoogleLoginStrategy`/`AppleLoginStrategy`/`KakaoLoginStrategy`에서 `FirebaseAuth.verifyIdToken()`을 사용해 실제 ID Token 검증.
   - Apple 로그인 시 `Sign in with Apple` revoke token 처리도 함께.
   - 이게 완료되면 신규 가입자의 `socialId`가 진짜 Firebase UID가 되어, 본 PR의 `deleteUser` 호출이 실제로 의미를 갖게 됨.

2. **타 도메인 추가 시 cascade 삭제**
   - Todo, Timer, Fuel, Exploration, Badge, Friends 등이 추가되는 PR에서 해당 도메인의 데이터 삭제 로직을 `AuthService.withdraw()`에 단순 호출 추가하거나, FK + `ON DELETE CASCADE`로 처리.

3. **Firebase 삭제 실패 retry**
   - `log.error`로 남기는 잔존 Firebase 유저를 후속 retry 큐(예: Spring Scheduler + 실패 테이블) 또는 운영 배치로 정리.

4. **운영 정책 변경 시**
   - Soft delete / grace period 도입: `Member`에 `withdrawnAt` 컬럼 추가 + 배치 잡으로 영구 삭제.
   - 탈퇴 사유 수집: 별도 DTO + 통계 테이블.

5. **profile별 FirebaseConfig 분기**
   - CI 빌드 환경 등 키 없이 빌드해야 하는 경우 `@Profile`로 빈 등록을 제외하거나, dummy `FirebaseAuth` 빈을 주입.

---

## 8. 참고

- API 공통 규칙: [`docs/api-specs/00_common.md`](../../api-specs/00_common.md)
- Auth API 상세 스펙: [`docs/api-specs/01_auth.md`](../../api-specs/01_auth.md) §4
- 동일 도메인 선행 스펙: [`2026-04-24-nickname-api-design.md`](./2026-04-24-nickname-api-design.md)
- Firebase Admin SDK Java 문서: https://firebase.google.com/docs/auth/admin/manage-users#delete_a_user
