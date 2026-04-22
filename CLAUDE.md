# SpaceStudyShip-BE 코드 컨벤션

## Claude 행동 규칙

- **커밋은 절대 임의로 하지 않는다. 반드시 사용자가 명시적으로 요청할 때만 커밋한다.**

---

## 커밋 메시지 컨벤션

모든 커밋 메시지는 아래 형식을 반드시 따른다. 이모지(이모디콘) 사용 금지.

```
{이슈제목} : {type} : {변경사항 설명} {이슈URL}
```

**type 목록:**

| type | 용도 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 구조 변경 (로직 유지) |
| `docs` | 문서, 주석, README |
| `chore` | 설정 파일, 빌드 관련 |
| `test` | 테스트 추가/수정 |
| `style` | 스타일, 포맷 |

예시:
```
소셜 로그인 구현 : feat : Google OAuth 연동 추가 https://github.com/.../issues/5
Swagger 접속 오류 수정 : fix : springdoc 버전 업그레이드 https://github.com/.../issues/7
의존성 구조 개선 : chore : 공통 의존성 SS-Common으로 통합
```

- 이슈가 없는 경우 이슈 URL 생략 가능
- **이모지(특수기호 포함) 커밋 메시지 사용 금지**

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
├── constant/     ← Enum, 상수 클래스
├── dto/          ← 해당 모듈의 모든 DTO (Controller/Service 공유)
├── entity/       ← JPA Entity
├── repository/
├── service/
└── {관심사}/     ← jwt/, social/, interceptor/ 등
```

- `constant/` 폴더: Enum 등 상수성 클래스는 반드시 여기 위치 (entity/ 금지)
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
| Controller 입력 | `{메소드명}Request` | `LoginRequest` |
| Controller 출력 | `{메소드명}Response` | `LoginResponse` |
| 내부 공유 / 값 객체 | `{의미단위}Dto` | `AuthMemberDto`, `MemberDto` |

### DTO 위치 규칙
- **모든 DTO는 반드시 해당 모듈의 `dto/` 폴더에 위치**
- Controller/Service 공유 DTO, Service 내부 전용 DTO 모두 `dto/`에 위치
- Service 내부에 private inner record로 DTO를 정의하지 않음

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

---

## Flyway 마이그레이션 규칙

**Flyway 마이그레이션으로만 스키마를 변경한다.**

> ⛔ **마이그레이션 파일에 민감한 값 절대 금지**
> API Key, 비밀번호, 토큰 등 민감한 실제 값을 SQL 파일에 직접 작성하지 않는다.

- 마이그레이션 파일 위치: `SS-Web/src/main/resources/db/migration/`
- 파일 네이밍: `V{version.yml의 version값}__{설명}.sql` — 점(`.`)은 언더스코어(`_`)로 치환
  - 예: `version: "0.0.1"` → `V0_0_1__init_schema.sql`
- **마이그레이션 파일 작성 전 반드시 프로젝트 루트 `version.yml`을 먼저 읽어 현재 버전을 확인한다**
- **하나의 version.yml 버전당 마이그레이션 파일은 반드시 1개만 작성한다**
- 이미 구현된 마이그레이션이 있으면 다시 만들지 않는다 — 이력 표를 먼저 확인 후 누락된 것만 추가
- `hibernate.ddl-auto`는 `update` 모드 — Flyway는 보조 수단, Hibernate가 실제 스키마를 관리
- **Entity를 추가/변경하면 반드시 대응하는 마이그레이션 파일을 함께 작성해야 한다**
- 기존 마이그레이션 파일은 절대 수정하지 말 것 (새 버전 파일로 추가)
- 테이블/컬럼 생성은 반드시 `IF NOT EXISTS` 사용, 삭제는 `IF EXISTS` 사용

### 현재 마이그레이션 이력

| 버전 | 파일 | 내용 |
|------|------|------|
| (없음) | - | 초기 상태 — Entity 구현 후 첫 마이그레이션 파일 작성 |

---

## 설계 문서

- `docs/plans/2026-03-09-refactor-module-structure-design.md` - 모듈 구조 리팩터링 설계
