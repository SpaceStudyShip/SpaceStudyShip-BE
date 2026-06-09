# Firebase ID Token 검증 적용 (소셜 로그인) 설계

## 배경 및 문제

`/api/auth/login` 호출 시 동일한 소셜 계정으로 매번 신규 회원가입이 발생하고 있다.

**근본 원인:** `SocialLoginStrategy` 구현체 3종 모두 idToken을 검증하지 않고 `ThreadLocalRandom` 으로 랜덤 socialId를 반환한다. `findBySocialIdAndSocialType()` 이 매번 빈 결과를 반환하므로 `findOrRegisterMember()` 가 신규 가입 분기를 탄다.

```java
// GoogleLoginStrategy.java:13-15 (현재)
// TODO: 구글 로그인 연동 구현
ThreadLocalRandom random = ThreadLocalRandom.current();
return "GOOGLE_SOCIAL_ID_" + random.nextInt(100_000);
```

`AppleLoginStrategy`, `KakaoLoginStrategy` 동일.

## 결정

프론트(Flutter)는 모든 소셜(Google/Apple/Kakao)에 대해 **Firebase Authentication 으로 인증한 뒤 Firebase ID Token 을 백엔드로 전송**한다. 따라서 백엔드는 소셜 종류와 무관하게 단일 검증 경로를 가진다.

```
firebaseAuth.verifyIdToken(idToken).getUid()
```

`getUid()` 가 반환하는 Firebase UID 를 `socialId` 로 사용한다. 사용자 별 영구 고유값이므로 동일 계정의 재로그인은 `findBySocialIdAndSocialType` 에서 정확히 매칭된다.

## 변경 범위

### 변경 파일

| 파일 | 변경 내용 |
|------|----------|
| `SS-Auth/.../auth/social/GoogleLoginStrategy.java` | 본문을 `firebaseAuth.verifyIdToken(idToken).getUid()` 호출로 교체. `FirebaseAuthException` 발생 시 `CustomException(ErrorCode.INVALID_TOKEN)` throw. `@RequiredArgsConstructor` + `private final FirebaseAuth firebaseAuth` |
| `SS-Auth/.../auth/social/AppleLoginStrategy.java` | 동일 |
| `SS-Auth/.../auth/social/KakaoLoginStrategy.java` | 동일 |

### 변경 없음

- `SocialLoginStrategy` 인터페이스: 유지
- `SocialLoginStrategyConfig`: 유지
- `LoginRequest`: `socialType` 필드 그대로 받음 (DB 의 `social_type` 분류용)
- `AuthService.getSocialId()`: 변경 없음
- `Member` 엔티티, DB 스키마, Flyway 마이그레이션: 변경 없음
- `ErrorCode.INVALID_TOKEN`: 기존 코드 재사용

### 부수 정리 (dev 환경)

dev DB 의 fake row(`GOOGLE_SOCIAL_ID_xxx`) 는 더 이상 의미 없는 더미 데이터다. **dev DB 만 수동 삭제** (마이그레이션 X):

```sql
DELETE FROM user_devices;
DELETE FROM members;
```

prod 는 아직 실 사용자가 없으므로 동일하게 비워두면 된다 (수동, 마이그레이션 아님).

## 보안 고려

- `socialType` 은 클라이언트가 보낸 값이지만 본 변경에서는 위변조 검증을 추가하지 않는다. 같은 Firebase UID 라면 항상 같은 row 로 매칭되므로 socialType 위변조로 인한 계정 탈취 위험은 없다.
- 더 엄격하게 가려면 `FirebaseToken.getClaims().get("firebase").sign_in_provider` 값(`google.com`, `apple.com`, `oidc.kakao` 등) 과 `socialType` 의 일치 여부를 검증할 수 있다. 본 변경 범위 밖.

## 에러 처리

| 케이스 | 처리 |
|--------|------|
| idToken 만료/서명 오류/형식 오류 | `FirebaseAuthException` → `CustomException(ErrorCode.INVALID_TOKEN)` |
| Firebase 서비스 일시 장애 | `FirebaseAuthException` 동일 분기 → `INVALID_TOKEN` |
| idToken `null` / 빈 문자열 | `LoginRequest` 입력 검증 단(이미 존재) 에서 거름. Strategy 단에서 추가 null 체크 불필요 |

## 테스트

### 변경 없는 테스트
- `AuthServiceTest`: `SocialLoginStrategy` 를 mock 하므로 그대로 통과해야 한다.

### 신규 테스트 (Strategy 단위)
각 Strategy 에 대해 최소 2 케이스:

1. **정상 검증** — `FirebaseAuth.verifyIdToken("valid")` 가 mock 으로 `FirebaseToken` 반환, `getUid()` 가 `"firebase-uid-1"` 반환 → strategy 가 `"firebase-uid-1"` 반환
2. **검증 실패** — `verifyIdToken` 이 `FirebaseAuthException` throw → `CustomException(INVALID_TOKEN)` 으로 변환되어 throw

3개 Strategy × 2 케이스 = 6 테스트. 짧음.

## 영향 평가

- API 스펙 변경 없음: `/api/auth/login` 의 request/response 구조 유지
- DB 스키마 변경 없음
- Flutter 측 추가 작업 없음 (이미 Firebase IdToken 전송 중)
- 기존 `AuthServiceTest`/`JwtTokenProviderTest`/`AuthControllerTest` 회귀 영향 없음

## 작업 순서

1. `GoogleLoginStrategy` 구현 + 단위 테스트
2. `AppleLoginStrategy` 구현 + 단위 테스트
3. `KakaoLoginStrategy` 구현 + 단위 테스트
4. `./gradlew test` 전체 통과 확인
5. dev DB cleanup (`DELETE FROM ...`) — 수동
6. 로컬에서 Flutter 로 로그인 → 같은 계정 두 번째 로그인 시 `isNewMember=false` 확인 (로그 `[SignUp]` 미출력)
