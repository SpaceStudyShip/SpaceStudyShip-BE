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

    // ---------- chargeFromStudy (30분 = 1연료) ----------

    @Test
    @DisplayName("chargeFromStudy: 30분 정확히 → 1연료 충전, pending=0")
    void chargeFromStudy_exactly30_charges1() {
        UserFuel fuel = UserFuel.initialize(1L);

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(30);

        assertThat(result.amount()).isEqualTo(1);
        assertThat(result.newPendingMinutes()).isZero();
        assertThat(fuel.getCurrentFuel()).isEqualTo(1);
        assertThat(fuel.getTotalCharged()).isEqualTo(1);
        assertThat(fuel.getPendingMinutes()).isZero();
    }

    @Test
    @DisplayName("chargeFromStudy: 25분 → 0연료, pending=25 누적 (transaction 없이 잔여분만 이월)")
    void chargeFromStudy_under30_pendingOnly() {
        UserFuel fuel = UserFuel.initialize(1L);

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(25);

        assertThat(result.amount()).isZero();
        assertThat(result.newPendingMinutes()).isEqualTo(25);
        assertThat(fuel.getCurrentFuel()).isZero();
        assertThat(fuel.getTotalCharged()).isZero();
        assertThat(fuel.getPendingMinutes()).isEqualTo(25);
    }

    @Test
    @DisplayName("chargeFromStudy: 90분 → 3연료, pending=0")
    void chargeFromStudy_multiple_3() {
        UserFuel fuel = UserFuel.initialize(1L);

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(90);

        assertThat(result.amount()).isEqualTo(3);
        assertThat(result.newPendingMinutes()).isZero();
        assertThat(fuel.getCurrentFuel()).isEqualTo(3);
    }

    @Test
    @DisplayName("chargeFromStudy: pending 25 + 20분 → 1연료, pending=15 (잔여분 이월 누적)")
    void chargeFromStudy_pendingCarriedOver() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.chargeFromStudy(25);   // pending=25

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(20);

        assertThat(result.amount()).isEqualTo(1);
        assertThat(result.newPendingMinutes()).isEqualTo(15);
        assertThat(fuel.getCurrentFuel()).isEqualTo(1);
        assertThat(fuel.getPendingMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("chargeFromStudy: pending 15 + 50분 → 2연료, pending=5")
    void chargeFromStudy_pendingPlusLargeStudy() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.chargeFromStudy(25);
        fuel.chargeFromStudy(20);   // 누적 currentFuel=1, pending=15

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(50);

        assertThat(result.amount()).isEqualTo(2);
        assertThat(result.newPendingMinutes()).isEqualTo(5);
        assertThat(fuel.getCurrentFuel()).isEqualTo(3);  // 0+1+2
        assertThat(fuel.getTotalCharged()).isEqualTo(3);
    }

    @Test
    @DisplayName("chargeFromStudy: studyMinutes<=0이면 INVALID_INPUT_VALUE")
    void chargeFromStudy_nonPositive_throws() {
        UserFuel fuel = UserFuel.initialize(1L);

        assertThatThrownBy(() -> fuel.chargeFromStudy(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> fuel.chargeFromStudy(-5))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
