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
    DEVICE_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "등록 가능한 디바이스 개수를 초과했습니다."),
    NICKNAME_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "랜덤 닉네임 생성에 실패했습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 회원을 찾을 수 없습니다."),
    DUPLICATED_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // Todo
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 할 일을 찾을 수 없습니다."),
    TODO_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 할 일이 이미 존재합니다."),

    // Todo Category
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 카테고리를 찾을 수 없습니다."),
    CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 ID의 카테고리가 이미 존재합니다."),

    // Fuel
    INSUFFICIENT_FUEL(HttpStatus.BAD_REQUEST, "연료가 부족합니다."),
    FUEL_NOT_INITIALIZED(HttpStatus.INTERNAL_SERVER_ERROR, "연료 정보가 초기화되지 않았습니다."),

    // Timer
    INVALID_SESSION_TIME(HttpStatus.BAD_REQUEST, "시작 시각이 종료 시각보다 늦거나 같습니다."),
    INVALID_DURATION(HttpStatus.BAD_REQUEST, "공부 시간이 시작/종료 시각 간격보다 큽니다."),
    SESSION_TOO_SHORT(HttpStatus.BAD_REQUEST, "공부 시간은 1분 이상이어야 합니다."),
    SESSION_TOO_LONG(HttpStatus.BAD_REQUEST, "공부 시간은 24시간(1440분)을 초과할 수 없습니다."),
    FUTURE_SESSION(HttpStatus.BAD_REQUEST, "미래 시각의 세션은 저장할 수 없습니다."),

    // Exploration
    PLANET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 행성을 찾을 수 없습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 지역을 찾을 수 없습니다."),
    ALREADY_UNLOCKED(HttpStatus.BAD_REQUEST, "이미 해금된 노드입니다."),
    PLANET_LOCKED(HttpStatus.BAD_REQUEST, "상위 행성이 아직 해금되지 않았습니다."),
    PREREQUISITE_NOT_CLEARED(HttpStatus.BAD_REQUEST, "이전 행성을 먼저 클리어해야 합니다."),

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 유효하지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문의 형식이 잘못되었습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메소드입니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 API를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
