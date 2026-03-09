# SpaceStudyShip-BE 코드 컨벤션

## 프로젝트 기본 정보
- **그룹 ID**: com.elipair
- **패키지**: com.elipair.spacestudyship
- **Java**: 21
- **Spring Boot**: 4.0.2
- **빌드**: Gradle 멀티모듈

---

## 모듈 구조

```
SS-Common     → 공통 라이브러리 (BaseTimeEntity, Exception, Util)
SS-Auth       → 인증 (JWT, 소셜로그인, Redis, Interceptor)
SS-Member     → 회원 (Entity, Repository)
SS-Study      → 학습 (미구현)
SS-Application → 통합 (미구현)
SS-Web        → 부트스트랩 (Controller, Config, main)
```

**Controller는 반드시 SS-Web 모듈에만 위치한다.**

---

## 패키지 구조 규칙

### SS-Web (Controller 위치)
```
SS-Web/controller/{도메인}/XxxController.java
```

### 도메인 모듈 (SS-Auth, SS-Member 등)
```
{모듈}/
├── dto/          ← 해당 모듈의 모든 DTO (Controller/Service 공유)
├── entity/       ← JPA Entity
├── repository/
├── service/
└── {관심사}/     ← jwt/, social/, interceptor/ 등
```

- `filter/` 폴더는 실제 Filter 클래스가 생길 때 추가한다.

---

## DTO 규칙

### 구현 방식
| 용도 | 방식 |
|------|------|
| DTO | **Record** |
| Entity | **@Builder 클래스** |

```java
// DTO - Record 사용
public record LoginRequest(String idToken, String socialType) {}
public record LoginResponse(String accessToken, String refreshToken, boolean isNewMember) {}
```

### 네이밍
| 용도 | 규칙 | 예시 |
|------|------|------|
| 입력 DTO | `{메소드명}Request` | `LoginRequest` |
| 출력 DTO | `{메소드명}Response` | `LoginResponse` |
| 공유 DTO | `{의미단위}Dto` | `MemberDto` |

### Controller ↔ Service DTO 공유
Controller와 Service는 **같은 DTO를 직접 사용**한다. Command/Result 중간 변환 없음.

```java
// Controller
public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
}

// Service
public LoginResponse login(LoginRequest request) { ... }
```

---

## Entity 규칙

```java
@Entity
@Table(name = "테이블명")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    // ...

    // 정적 팩토리 메소드로 생성 의도 표현
    public static Member signUp(String socialId, SocialType socialType, String nickname) {
        return Member.builder()
                .socialId(socialId)
                .socialType(socialType)
                .nickname(nickname)
                .build();
    }
}
```

- `@SuperBuilder` 사용 금지
- 기본 생성자는 반드시 `AccessLevel.PROTECTED`

---

## Lombok 사용 규칙

| 어노테이션 | 사용 위치 |
|-----------|----------|
| `@Getter` | Entity, 필요한 클래스 |
| `@Builder` | Entity |
| `@RequiredArgsConstructor` | Service, Component (생성자 주입) |
| `@Slf4j` | 로그 필요한 클래스 |
| `@Data` | 사용 금지 |
| `@SuperBuilder` | 사용 금지 |

---

## 예외 처리 규칙

```java
// 예외 발생
throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);

// Repository 커스텀 메소드 네이밍
getByXxx()   // 없으면 CustomException 던짐
findByXxx()  // Optional 반환
```

---

## 로깅 규칙

```java
@Slf4j
// ...
log.info("[Action] 설명 | key=value, key=value");
log.warn("[Action] 경고 설명");
```

---

## 응답 규칙

- Controller는 `ResponseEntity<T>` 반환
- 생성(신규 회원 등): `HttpStatus.CREATED`
- 삭제/로그아웃: `ResponseEntity.noContent().build()`

---

## 설계 문서

- `docs/plans/2026-03-09-refactor-module-structure-design.md` - 모듈 구조 리팩터링 설계
