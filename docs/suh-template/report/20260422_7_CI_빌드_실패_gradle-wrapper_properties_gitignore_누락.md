# ❗[버그][CICD] CI 빌드 실패 - gradle-wrapper.properties gitignore 누락

## 개요

`.gitignore`의 `*.properties` 규칙이 `gradle/wrapper/gradle-wrapper.properties`를 추적 대상에서 제외해 CI 빌드가 전면 실패하던 문제를 gitignore 예외 규칙 추가로 해결했다.

## 변경 사항

### gitignore 수정
- `.gitignore`: `*.properties` 규칙 아래 `!gradle/wrapper/gradle-wrapper.properties` 예외 추가

## 주요 구현 내용

기존 `.gitignore`에 `*.properties` 전체 무시 규칙이 있어 Gradle Wrapper 설정 파일이 Git에 포함되지 않았다. CI 환경에서는 로컬과 달리 해당 파일이 없어 `Wrapper properties file does not exist` 에러가 발생했다. `!gradle/wrapper/gradle-wrapper.properties` 예외 규칙을 추가해 파일이 Git에 추적되도록 수정했다.

## 주의사항

- `gradle-wrapper.jar`도 동일하게 예외 처리되어 있어 Wrapper 전체가 정상 추적됨
