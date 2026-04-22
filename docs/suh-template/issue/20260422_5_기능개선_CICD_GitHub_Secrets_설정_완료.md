---
name: 🚀[기능개선][DevOps] CICD GitHub Secrets 설정 및 워크플로우 검증
description: GitHub Actions CICD 워크플로우에 필요한 Secrets 등록 완료 및 설정 검증 이슈
type: project
---

# 🚀[기능개선][DevOps] CICD GitHub Secrets 설정 및 워크플로우 검증

- 라벨: 작업완료
- 담당자: Cassiiopeia

📝 현재 문제점
---

- GitHub Actions CICD 워크플로우(`PROJECT-SPRING-SYNOLOGY-SIMPLE-CICD.yaml`, `PROJECT-SPRING-SYNOLOGY-PR-PREVIEW.yaml`)가 GitHub Secrets 미설정으로 실제 동작 불가 상태
- `APPLICATION_PROD_YML` Secret 미설정으로 빌드 시 `application-prod.yml` 파일 생성 불가
- DockerHub 인증 정보(`DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`) 미설정으로 이미지 빌드/푸시 불가
- Synology NAS SSH 접속 정보(`SERVER_HOST`, `SERVER_USER`, `SERVER_PASSWORD`) 미설정으로 자동 배포 불가

🛠️ 해결 방안 / 제안 기능
---

- 아래 6개 GitHub Secrets를 레포지토리에 등록하여 CICD 파이프라인 정상 동작 보장
  - `APPLICATION_PROD_YML`: `application-prod.yml` 전체 내용 (gitignore로 제외된 파일)
  - `DOCKERHUB_USERNAME`: DockerHub 사용자명
  - `DOCKERHUB_TOKEN`: DockerHub 액세스 토큰
  - `SERVER_HOST`: Synology NAS 배포 서버 주소
  - `SERVER_USER`: SSH 접속 사용자명
  - `SERVER_PASSWORD`: SSH 접속 비밀번호

⚙️ 작업 내용
---

- GitHub 레포지토리 Settings → Secrets and variables → Actions에서 6개 Secret 등록
- `deploy` 브랜치 push 시 자동 CICD 동작 여부 검증
- PR Preview 워크플로우(`@suh-lab server build`) 동작 여부 검증

🙋‍♂️ 담당자
---

- 백엔드: Cassiiopeia
