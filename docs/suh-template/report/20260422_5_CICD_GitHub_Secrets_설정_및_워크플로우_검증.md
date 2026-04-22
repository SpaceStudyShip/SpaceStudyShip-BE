# 🚀[기능개선][DevOps] CICD GitHub Secrets 설정 및 워크플로우 검증

## 개요

GitHub Actions CICD 파이프라인 동작에 필요한 6개 Secrets를 등록하고, prod 프로파일 기본 활성화 및 Swagger 외부 접근을 허용하여 자동 배포 파이프라인을 정상화했다.

## 변경 사항

### GitHub Secrets 등록
- `APPLICATION_PROD_YML`: `application-prod.yml` 빌드 시 주입
- `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN`: DockerHub 이미지 빌드 및 푸시 인증
- `SERVER_HOST` / `SERVER_USER` / `SERVER_PASSWORD`: Synology NAS SSH 배포 접속 정보

### Spring Boot 설정
- `SS-Web/src/main/resources/application.yml`: 기본 프로파일을 `prod`로 변경
- `SS-Web/src/main/java/.../config/SecurityConfig.java`: Swagger UI 경로 외부 접근 허용
- `SS-Web/src/main/java/.../config/SwaggerConfig.java`: Production 서버 URL 등록 (`http://suh-project.synology.me:8099`)

### CICD 워크플로우
- `.github/workflows/PROJECT-SPRING-SYNOLOGY-SIMPLE-CICD.yaml`: deploy 브랜치 생성 및 배포 포트 8099 설정

## 주요 구현 내용

`APPLICATION_PROD_YML` Secret은 CI 빌드 단계에서 `application-prod.yml`로 파일 생성되어 주입된다. prod 프로파일이 기본값으로 설정되어 별도 환경 변수 없이 배포 환경에서 자동으로 prod 설정이 적용된다. SecurityConfig에서 `/docs/**`, `/swagger-ui/**`, `/v3/api-docs/**` 경로를 인증 없이 접근 가능하도록 허용했다.

## 주의사항

- 배포 포트를 8096 → 8099로 변경 (기존 mapsy-back과 충돌)
- Synology 역방향 프록시에서 `api.spacestudyship.suhsaechan.kr:443 → localhost:8099` 설정 필요
