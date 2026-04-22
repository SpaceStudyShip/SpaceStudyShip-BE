---
name: 🚀[기능개선][Security] Security 허용 경로 WhiteList 클래스 분리 관리
description: SecurityConfig 내 하드코딩된 허용 경로를 SecurityUrls 상수 클래스로 분리하여 중앙 관리
type: project
---

# 🚀[기능개선][Security] Security 허용 경로 WhiteList 클래스 분리 관리

- 라벨: 작업전
- 담당자: Cassiiopeia

📝 현재 문제점
---

- `SecurityConfig`에 인증 제외 URL 패턴이 직접 하드코딩되어 있어 경로 추가/변경 시 SecurityConfig를 직접 수정해야 함
- 허용 경로가 늘어날수록 SecurityConfig가 비대해지고 역할이 혼재됨

🛠️ 해결 방안 / 제안 기능
---

- `SS-Auth/constant/SecurityUrls.java` 상수 클래스를 신규 생성하여 허용 경로를 그룹별로 중앙 관리
- `SecurityConfig`는 `SecurityUrls.AUTH_WHITELIST`만 참조하도록 변경
- 이후 경로 추가/변경은 `SecurityUrls.java`만 수정하면 되도록 구조 개선

⚙️ 작업 내용
---

- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/constant/SecurityUrls.java` 생성
  - `AUTH_WHITELIST`: Actuator, 인증 API, Swagger 관련 경로 목록 관리
- `SS-Web/src/main/java/com/elipair/spacestudyship/config/SecurityConfig.java` 리팩터링
  - 하드코딩 경로 제거 → `SecurityUrls.AUTH_WHITELIST` 참조로 교체

🙋‍♂️ 담당자
---

- 백엔드: Cassiiopeia
