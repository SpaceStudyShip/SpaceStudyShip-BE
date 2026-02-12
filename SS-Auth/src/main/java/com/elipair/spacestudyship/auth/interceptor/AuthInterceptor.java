package com.elipair.spacestudyship.auth.interceptor;

import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.util.AuthorizationExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String accessToken = AuthorizationExtractor.extractToken(request)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST));

        Long memberId = jwtTokenProvider.getMemberIdFromAccessToken(accessToken);
        request.setAttribute("loginMember", new LoginMember(memberId));

        return true;
    }
}
