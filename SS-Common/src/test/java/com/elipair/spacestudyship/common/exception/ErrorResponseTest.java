package com.elipair.spacestudyship.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    @DisplayName("of(ErrorCode): requiredFuel/currentFuel은 null")
    void of_basic_nullFuelFields() {
        ErrorResponse r = ErrorResponse.of(ErrorCode.PLANET_NOT_FOUND);
        assertThat(r.code()).isEqualTo("PLANET_NOT_FOUND");
        assertThat(r.requiredFuel()).isNull();
        assertThat(r.currentFuel()).isNull();
    }

    @Test
    @DisplayName("ofInsufficientFuel: 연료 수치 포함")
    void ofInsufficientFuel_includesAmounts() {
        ErrorResponse r = ErrorResponse.ofInsufficientFuel("연료가 부족합니다.", 10, 4);
        assertThat(r.code()).isEqualTo("INSUFFICIENT_FUEL");
        assertThat(r.requiredFuel()).isEqualTo(10);
        assertThat(r.currentFuel()).isEqualTo(4);
    }

    @Test
    @DisplayName("InsufficientFuelException: 게터로 수치 노출")
    void exception_getters() {
        InsufficientFuelException ex = new InsufficientFuelException(10, 4);
        assertThat(ex.getRequiredFuel()).isEqualTo(10);
        assertThat(ex.getCurrentFuel()).isEqualTo(4);
    }
}
