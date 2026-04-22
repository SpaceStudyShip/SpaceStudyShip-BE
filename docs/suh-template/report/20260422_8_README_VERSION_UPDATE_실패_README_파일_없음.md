# ❗[버그][CICD] README VERSION UPDATE 실패 - README.md 파일 없음

## 개요

프로젝트 루트에 `README.md`가 없어 버전 업데이트 워크플로우가 실패하던 문제를 README.md 생성 및 불필요한 Nexus 워크플로우 삭제로 해결했다.

## 변경 사항

### 파일 추가
- `README.md`: 프로젝트 루트에 기본 README 생성 (프로젝트 소개, 버전 정보 포함)

### 워크플로우 정리
- `.github/workflows/PROJECT-SPRING-NEXUS-PUBLISH.yaml`: 라이브러리 배포용으로 이 프로젝트와 무관한 워크플로우 삭제

## 주요 구현 내용

`PROJECT-COMMON-README-VERSION-UPDATE` 워크플로우는 deploy 브랜치 push 시 `README.md`에서 버전 정보를 찾아 업데이트한다. 파일 자체가 없어 `grep: README.md: No such file or directory` 에러가 발생했으며, README.md를 생성하여 해결했다. 동시에 Spring 라이브러리 Nexus 배포용 워크플로우는 이 프로젝트(애플리케이션 서버)에 불필요하여 함께 제거했다.

## 주의사항

- README.md에 `<!-- version -->` 태그 형식이 포함되어야 버전 자동 업데이트가 정상 동작함
