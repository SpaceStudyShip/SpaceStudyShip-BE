package com.elipair.spacestudyship.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Auth
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패하였습니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 만료되었습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
    UNAUTHENTICATED_REQUEST(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    UNSUPPORTED_SOCIAL_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 방식입니다."),
    NICKNAME_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "랜덤 닉네임 생성에 실패했습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 회원을 찾을 수 없습니다."),
    DUPLICATED_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 유효하지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문의 형식이 잘못되었습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메소드입니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 API를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
