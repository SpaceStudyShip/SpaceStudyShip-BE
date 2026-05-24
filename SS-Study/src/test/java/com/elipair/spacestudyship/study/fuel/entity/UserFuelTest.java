package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFuelTest {

    @Test
    @DisplayName("initialize: 신규 회원 초기화 시 모든 값 0")
    void initialize_allZero() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThat(fuel.getUserId()).isEqualTo(1L);
        assertThat(fuel.getCurrentFuel()).isZero();
        assertThat(fuel.getTotalCharged()).isZero();
        assertThat(fuel.getTotalConsumed()).isZero();
        assertThat(fuel.getPendingMinutes()).isZero();
    }

    @Test
    @DisplayName("charge: 양수 충전 시 currentFuel과 totalCharged 증가, totalConsumed 불변")
    void charge_increase() {
        UserFuel fuel = UserFuel.initialize(1L);

        fuel.charge(90);

        assertThat(fuel.getCurrentFuel()).isEqualTo(90);
        assertThat(fuel.getTotalCharged()).isEqualTo(90);
        assertThat(fuel.getTotalConsumed()).isZero();
    }

    @Test
    @DisplayName("consume: 잔량 이하 소비 시 currentFuel 감소, totalConsumed 증가, totalCharged 불변")
    void consume_decrease() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);

        fuel.consume(50);

        assertThat(fuel.getCurrentFuel()).isEqualTo(50);
        assertThat(fuel.getTotalConsumed()).isEqualTo(50);
        assertThat(fuel.getTotalCharged()).isEqualTo(100);
    }

    @Test
    @DisplayName("consume: 정확히 잔량만큼 소비 시 currentFuel = 0")
    void consume_exact() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);

        fuel.consume(100);

        assertThat(fuel.getCurrentFuel()).isZero();
        assertThat(fuel.getTotalConsumed()).isEqualTo(100);
    }

    @Test
    @DisplayName("charge: amount=0이면 INVALID_INPUT_VALUE")
    void charge_zero_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.charge(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("charge: amount<0이면 INVALID_INPUT_VALUE")
    void charge_negative_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.charge(-5))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("consume: amount=0이면 INVALID_INPUT_VALUE")
    void consume_zero_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.consume(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("consume: 잔량 부족 시 INSUFFICIENT_FUEL")
    void consume_insufficient_throws() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(30);

        assertThatThrownBy(() -> fuel.consume(50))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUEL);
    }
}
