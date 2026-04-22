# ❗[버그][Security] Spring Boot 4 UserDetailsService 미등록 시 Security 설정 미적용

- **라벨**: 작업전
- **담당자**: Cassiiopeia

---

🗒️ 설명
---

- Spring Boot 4.x (Spring Security 6.x) 환경에서 `SecurityFilterChain` 빈을 등록했음에도 `UserDetailsService` 빈이 없으면 `UserDetailsServiceAutoConfiguration`이 `inMemoryUserDetailsManager`를 자동 생성한다.
- 이로 인해 커스텀 `SecurityConfig`의 whitelist(permitAll) 설정이 제대로 동작하지 않아 Swagger UI(`/docs/swagger`)에 접근 시 **401 Unauthorized**가 반환된다.
- 서버 로그에 `Using generated security password` WARN이 출력되며, `/actuator/health` 헬스체크도 인증에 막혀 CI/CD 배포 헬스체크가 실패한다.

🔄 재현 방법
---

1. Spring Boot 4.x 프로젝트에서 `SecurityFilterChain` 빈만 등록하고 `UserDetailsService` 빈은 등록하지 않음
2. 서버 기동
3. `Using generated security password: ...` WARN 로그 확인
4. whitelist에 등록된 `/docs/swagger` 접근 → **401** 응답 확인
5. `/actuator/health` 접근 → **401** 응답으로 CI/CD 헬스체크 실패

📸 참고 자료
---

```
WARN: Using generated security password: d4f40d74-eb86-4fd9-bd3a-4e0efb9c44d3
WARN: Global AuthenticationManager configured with UserDetailsService bean with name inMemoryUserDetailsManager
```

- Spring Boot 3.x까지는 `SecurityFilterChain`만 등록해도 auto-configuration이 비활성화되었으나, 4.x에서는 `UserDetailsService`도 명시적으로 등록해야 함

✅ 예상 동작
---

- `SecurityFilterChain` 빈이 등록되어 있으면 커스텀 Security 설정이 우선 적용되어야 함
- whitelist에 등록된 경로(`/docs/swagger`, `/actuator/health` 등)는 인증 없이 접근 가능해야 함

⚙️ 환경 정보
---

- **Spring Boot**: 4.0.2-SNAPSHOT
- **Spring Security**: 6.x
- **Java**: 21
- **배포 환경**: Docker (prod profile)

🙋‍♂️ 담당자
---

- **백엔드**: Cassiiopeia
