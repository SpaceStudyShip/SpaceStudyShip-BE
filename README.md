# SpaceStudyShip-BE

> 우주공부선(Space Study Ship) 백엔드 — 공부한 시간을 연료로 바꿔 우주를 탐험하는 게이미피케이션 Todo + Timer 앱의 API 서버

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
## 최신 버전 : v0.0.43 (2026-06-09)

앱 소개와 스크린샷은 [프론트엔드 저장소 (SpaceStudyShip-FE)](https://github.com/SpaceStudyShip/SpaceStudyShip-FE)에 있습니다.

## 개요

할 일 · 타이머로 쌓은 공부 기록을 저장하고, 공부 시간에 따라 연료를 지급하며, 그 연료로 행성과 지역을 해금하는 기능을 제공합니다. 회원은 Google · Apple 로그인(Firebase ID 토큰 검증)으로 가입하며, 기기별로 세션과 FCM 토큰을 관리합니다.

- **연료 규칙** — 타이머 30분마다 연료 1, 30분이 안 되는 남은 분은 다음 기록으로 이월. 앱의 게스트 모드에서 쓰던 규칙을 서버도 그대로 따릅니다.
- **중복 지급 방지** — 타이머 기록 저장 요청에 `Idempotency-Key`를 받아, 이미 처리한 키로 다시 오면 기존 결과를 돌려줍니다. 연료 쪽에서도 거래 ID로 중복을 한 번 더 막습니다.
- **정합성** — 기록 저장 · 연료 충전 · 할 일 학습시간 반영을 하나의 트랜잭션으로 처리하고, 행성 · 지역 해금도 연료 차감과 함께 처리합니다.
- **기기별 세션** — Refresh Token을 기기 테이블(`user_devices`)에 저장하고, 토큰에 기기 ID를 담아 로그아웃 · 재발급 시 기기를 구분합니다.

## 기술 스택

- **Java** 21
- **Spring Boot** 4.0.2 (Spring Web, Data JPA, Security, Validation, Actuator)
- **PostgreSQL** 16 + **Flyway**
- **JWT** (jjwt 0.12.6), **Firebase Admin SDK** 9.4.3
- **springdoc-openapi** 2.8.3 (Swagger UI)
- **Docker**, **GitHub Actions** (`deploy` 브랜치 push 시 배포)
- 테스트: JUnit 5, Testcontainers (PostgreSQL)

## 모듈 구조

| 모듈 | 역할 |
|------|------|
| SS-Common | 공통 라이브러리 (BaseTimeEntity, 예외 처리, 공용 의존성) |
| SS-Auth | 인증 (Firebase ID 토큰 검증, JWT, 기기 · FCM 토큰 관리, 닉네임 변경, 회원 탈퇴) |
| SS-Member | 회원 (Entity, Repository, 가입 이벤트) |
| SS-Study | 학습 도메인 (할 일 · 카테고리, 타이머 세션, 연료, 탐험) |
| SS-Application | 통합 모듈 (아직 소스 없음) |
| SS-Web | 부트스트랩 (Controller, Config, main) |

Controller는 SS-Web 모듈의 `controller/{도메인}/`에만 둡니다. 자세한 컨벤션은 [CLAUDE.md](CLAUDE.md)를 참고하세요.

## API

전체 23개 엔드포인트입니다. 상세 명세는 [docs/api-specs](docs/api-specs)에 있습니다.

| 도메인 | 엔드포인트 |
|--------|-----------|
| 인증 (6) | `POST /api/auth/login` · `POST /api/auth/reissue` · `POST /api/auth/logout` · `GET /api/auth/check-nickname` · `PATCH /api/auth/nickname` · `DELETE /api/auth/withdraw` |
| 할 일 카테고리 (4) | `GET` · `POST /api/todo-categories`, `PATCH` · `DELETE /api/todo-categories/{categoryId}` |
| 할 일 (4) | `GET` · `POST /api/todos`, `PATCH` · `DELETE /api/todos/{todoId}` |
| 타이머 세션 (3) | `POST` · `GET /api/timer-sessions`, `GET /api/timer-sessions/today-stats` |
| 연료 (2) | `GET /api/fuel`, `GET /api/fuel/transactions` |
| 탐험 (4) | `GET /api/explorations/planets`, `GET /api/explorations/planets/{planetId}/regions`, `POST /api/explorations/planets/{planetId}/unlock`, `POST /api/explorations/regions/{regionId}/unlock` |

배지와 소셜 API는 명세([06_badge.md](docs/api-specs/06_badge.md), [07_social.md](docs/api-specs/07_social.md))만 있고 아직 구현되지 않았습니다.

## 로컬 실행

`application-dev.yml` 등 프로파일별 설정 파일과 Firebase Admin SDK 키 파일은 저장소에 포함되어 있지 않습니다. 팀에서 받아 `SS-Web/src/main/resources/` 아래에 두어야 합니다.

```bash
# Docker Compose로 PostgreSQL + Redis 실행
docker compose up -d

# 빌드 및 실행 (dev 프로파일, 기본 프로파일은 prod)
./gradlew clean build -x test
java -Dspring.profiles.active=dev -jar SS-Web/build/libs/app.jar

# 테스트 (SS-Study 테스트는 Testcontainers를 쓰므로 Docker가 필요합니다)
./gradlew test
```

- Swagger UI: `http://localhost:8080/docs/swagger`
- 스키마는 Flyway 마이그레이션(`SS-Web/src/main/resources/db/migration/`)으로 관리합니다.

## 문서

- [API 스펙](docs/api-specs) — 공통, 인증, 할 일, 타이머, 연료, 탐험, 배지, 소셜
- [설계 문서](docs/superpowers/specs) — 닉네임, 회원 탈퇴, 기기 · FCM 토큰, Firebase ID 토큰 검증, 할 일, 연료, 타이머 세션, 오늘 통계, 탐험

## 기여자

| 이름 | 역할 |
|------|------|
| [홍의민 (EM-H20)](https://github.com/EM-H20) | 닉네임 확인 · 변경, 회원 탈퇴, 기기 · FCM 토큰 저장, Firebase ID 토큰 검증, 할 일 · 카테고리, 연료, 타이머 세션, 오늘 통계, 탐험 도메인 구현 |
| [SUH SAECHAN (Cassiiopeia)](https://github.com/Cassiiopeia) | 멀티모듈 초기 구조, Security · Swagger 설정, CI/CD · 배포, API 스펙 문서 |
| yeo-li | 저장소 초기 커밋 |

## 라이선스

[SpaceStudyShip Source Available License](LICENSE)
