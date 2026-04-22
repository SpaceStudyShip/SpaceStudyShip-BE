---
name: ❗[버그][CICD] CI 빌드 실패 - gradle-wrapper.properties gitignore 누락
description: *.properties gitignore 규칙으로 gradle-wrapper.properties가 Git 미추적되어 CI 빌드 실패
type: project
---

# ❗[버그][CICD] CI 빌드 실패 - gradle-wrapper.properties gitignore 누락

- 라벨: 작업전
- 담당자: Cassiiopeia

🗒️ 설명
---

- `.gitignore`에 `*.properties` 규칙이 있어 `gradle/wrapper/gradle-wrapper.properties`가 Git에 추적되지 않음
- CI 빌드 시 `gradle-wrapper.properties does not exist` 에러 발생하며 빌드 전체 실패
- `PROJECT-SPRING-SYNOLOGY-CICD` 워크플로우 Run #2에서 확인

🔄 재현 방법
---

1. `deploy` 브랜치에 push
2. `PROJECT-SPRING-SYNOLOGY-CICD` 워크플로우 트리거
3. `Run ./gradlew clean build` 단계에서 에러 발생

📸 참고 자료
---

```
Error: Exception in thread "main" java.lang.RuntimeException:
Wrapper properties file '.../gradle/wrapper/gradle-wrapper.properties' does not exist.
Error: Process completed with exit code 1.
```

✅ 예상 동작
---

- `gradle-wrapper.properties`가 Git에 추적되어 CI 빌드 정상 동작

⚙️ 환경 정보
---

- **파일**: `.gitignore` — `*.properties` 규칙
- **영향 파일**: `gradle/wrapper/gradle-wrapper.properties`
- **워크플로우**: `PROJECT-SPRING-SYNOLOGY-SIMPLE-CICD.yaml`

🙋‍♂️ 담당자
---

- **백엔드**: Cassiiopeia
