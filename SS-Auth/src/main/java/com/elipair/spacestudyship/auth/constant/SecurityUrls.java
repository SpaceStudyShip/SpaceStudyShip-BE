package com.elipair.spacestudyship.auth.constant;

import java.util.List;

/**
 * Security 인증 제외 URL 상수 관리
 * SecurityConfig에서 이 클래스만 수정해 허용 경로를 관리한다
 */
public class SecurityUrls {

    /**
     * 인증 없이 접근 허용할 URL 패턴
     */
    public static final List<String> AUTH_WHITELIST = List.of(

        // Actuator
        "/actuator/health",

        // 인증 API
        "/api/auth/**",

        // Swagger UI (springdoc 내부 리소스 포함)
        "/docs/swagger",
        "/docs/swagger/**",
        "/docs/api-docs",
        "/docs/api-docs/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/webjars/**"
    );
}
