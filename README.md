# SpaceStudyShip-BE

> 스터디 그룹 매칭 및 관리 서비스 백엔드

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
## 최신 버전 : v0.0.16 (2026-04-22)

## 기술 스택

- **Java** 21
- **Spring Boot** 4.0.2
- **PostgreSQL** 16
- **Redis** 7
- **Flyway**
- **Docker**

## 모듈 구조

| 모듈 | 역할 |
|------|------|
| SS-Common | 공통 라이브러리 (BaseTimeEntity, Exception, Util) |
| SS-Auth | 인증 (JWT, 소셜로그인, Redis, Interceptor) |
| SS-Member | 회원 (Entity, Repository) |
| SS-Study | 학습 (미구현) |
| SS-Application | 통합 (미구현) |
| SS-Web | 부트스트랩 (Controller, Config, main) |

## 로컬 실행

```bash
# Docker Compose로 PostgreSQL + Redis 실행
docker compose up -d

# 빌드 및 실행 (dev 프로파일)
./gradlew clean build -x test
java -Dspring.profiles.active=dev -jar SS-Web/build/libs/app.jar
```

## API 문서

- Swagger UI: `http://localhost:8080/docs/swagger` (dev 프로파일에서만 활성화)
