package com.elipair.spacestudyship.auth.interceptor;

import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.util.AuthorizationExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String method = request.getMethod();
        String uri = request.getRequestURI();
        log.debug("[Auth] preHandle 진입 | method={}, uri={}", method, uri);

        String accessToken = AuthorizationExtractor.extractToken(request)
                .orElseThrow(() -> {
                    log.warn("[Auth] 인증 헤더 누락 | method={}, uri={}", method, uri);
                    return new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST);
                });

        try {
            Long memberId = jwtTokenProvider.getMemberIdFromAccessToken(accessToken);
            request.setAttribute("loginMember", new LoginMember(memberId));
            log.debug("[Auth] 인증 통과 | memberId={}, method={}, uri={}", memberId, method, uri);
            return true;
        } catch (CustomException e) {
            log.warn("[Auth] 토큰 검증 실패 | code={}, method={}, uri={}",
                    e.getErrorCode().name(), method, uri);
            throw e;
        }
    }
}
