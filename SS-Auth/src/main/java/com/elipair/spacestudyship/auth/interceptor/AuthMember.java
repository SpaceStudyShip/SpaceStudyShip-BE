package com.elipair.spacestudyship.auth.interceptor;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인된 사용자의 {@link LoginMember}를 컨트롤러 파라미터에 주입한다.
 * <p>
 * {@link io.swagger.v3.oas.annotations.Parameter @Parameter(hidden = true)} 메타 어노테이션으로
 * 인해 이 어노테이션이 붙은 파라미터는 Swagger 문서에 노출되지 않는다.
 * 실제 인증 정보는 {@code Authorization: Bearer <token>} 헤더로 전달되며,
 * 서버 내부에서 토큰을 파싱하여 {@code LoginMember} 객체를 만든다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface AuthMember {
}
