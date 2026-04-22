# ❗[버그][Security] Spring Boot 4 UserDetailsService 미등록 시 Security 설정 미적용

## 개요

Spring Boot 4.x 환경에서 두 가지 Security 관련 버그가 발생했다.
첫째, `SecurityFilterChain` 빈만 등록하고 `UserDetailsService` 빈을 등록하지 않으면 `UserDetailsServiceAutoConfiguration`이 `inMemoryUserDetailsManager`를 자동 생성하여 커스텀 Security 설정이 무시된다.
둘째, springdoc의 커스텀 Swagger 경로(`/docs/swagger`) 설정 시 내부적으로 `/docs/swagger-ui/**` 경로로 리다이렉트되는데, 이 경로가 Security whitelist에 누락되어 Swagger UI 접근 시 401이 반환됐다.
두 문제 모두 수정하여 Swagger UI와 헬스체크 엔드포인트가 정상 동작하도록 해결했다.

## 변경 사항

### Security 설정
- `SS-Web/src/main/java/com/elipair/spacestudyship/config/SecurityConfig.java`: 빈 `InMemoryUserDetailsManager`를 `UserDetailsService` 빈으로 명시 등록하여 `UserDetailsServiceAutoConfiguration` 자동 생성 억제

### Security Whitelist 경로
- `SS-Auth/src/main/java/com/elipair/spacestudyship/auth/constant/SecurityUrls.java`: `/docs/swagger-ui/**` 경로 추가 — springdoc 커스텀 path 사용 시 내부 리소스가 이 경로로 서빙됨. `/swagger-ui.html`, `/favicon.ico`도 함께 추가

## 주요 구현 내용

**버그 1: UserDetailsService 미등록**

Spring Boot 3.x까지는 `SecurityFilterChain` 빈만 있어도 Security auto-configuration이 비활성화됐다. Spring Boot 4.x에서는 `UserDetailsService` 빈이 없으면 `UserDetailsServiceAutoConfiguration`이 여전히 `inMemoryUserDetailsManager`를 생성하여 커스텀 whitelist 설정이 무시된다.

```java
// UserDetailsServiceAutoConfiguration의 자동 생성을 막기 위해 빈을 직접 등록
@Bean
public UserDetailsService userDetailsService() {
    return new InMemoryUserDetailsManager();
}
```

**버그 2: Swagger whitelist 경로 누락**

springdoc이 `swagger-ui.path: /docs/swagger`로 설정된 경우, `/docs/swagger` 접근 시 내부적으로 `/docs/swagger-ui/index.html?configUrl=...` 경로로 리다이렉트한다. 기존 whitelist에는 `/swagger-ui/**`만 있었고 `/docs/swagger-ui/**`가 없어서 Security 레이어에서 차단됐다. 서버 로그에 요청 자체가 찍히지 않는 것으로 확인 — Spring MVC까지 도달하기 전에 Security 필터에서 401 반환.

```java
"/docs/swagger-ui/**",  // 커스텀 path 사용 시 springdoc 내부 리소스 경로
```

## 주의사항

- springdoc `swagger-ui.path`를 커스텀 경로로 변경하면 내부 리소스 경로도 해당 prefix를 따라간다. whitelist 작성 시 반드시 `/{커스텀prefix}/swagger-ui/**` 패턴을 함께 추가해야 한다.
- Spring Boot 4.x 업그레이드 시 `Using generated security password` 로그가 보이면 `UserDetailsService` 빈 등록 여부를 확인한다.
- CI/CD 헬스체크 대기 시간(`HEALTHCHECK_WAIT_SECONDS`)도 10초 → 30초로 함께 수정됐다.
