package com.elipair.spacestudyship.common.exception;

public record ErrorResponse(
        int status,
        String message
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getHttpStatus().value(), errorCode.getMessage());
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message);
    }
}
