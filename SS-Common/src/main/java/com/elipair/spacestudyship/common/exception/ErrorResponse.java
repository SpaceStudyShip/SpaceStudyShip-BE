package com.elipair.spacestudyship.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Integer requiredFuel,
        Integer currentFuel
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, null, null);
    }

    public static ErrorResponse ofInsufficientFuel(String message, int requiredFuel, int currentFuel) {
        return new ErrorResponse(ErrorCode.INSUFFICIENT_FUEL.name(), message, requiredFuel, currentFuel);
    }
}
