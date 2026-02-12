package com.elipair.spacestudyship.auth.interceptor;

import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.util.AuthorizationExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(LoginMember.class)
                && parameter.hasParameterAnnotation(AuthMember.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        LoginMember loginMember = (LoginMember) request.getAttribute("loginMember");
        if (loginMember != null) {
            return loginMember;
        }

        return getLoginMemberFromAccessToken(request);
    }

    private LoginMember getLoginMemberFromAccessToken(HttpServletRequest request) {
        String accessToken = AuthorizationExtractor.extractToken(request)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST));
        Long memberId = jwtTokenProvider.getMemberIdFromAccessToken(accessToken);
        return new LoginMember(memberId);
    }
}
