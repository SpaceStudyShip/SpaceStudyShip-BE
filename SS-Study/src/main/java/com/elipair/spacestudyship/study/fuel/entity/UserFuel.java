package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

@Entity
@Checks({
        @Check(name = "chk_fuel_non_negative", constraints = "current_fuel >= 0"),
        @Check(name = "chk_total_charged_non_negative", constraints = "total_charged >= 0"),
        @Check(name = "chk_total_consumed_non_negative", constraints = "total_consumed >= 0"),
        @Check(name = "chk_pending_minutes_non_negative", constraints = "pending_minutes >= 0")
})
@Table(name = "user_fuel")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFuel extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_fuel", nullable = false)
    private Integer currentFuel;

    @Column(name = "total_charged", nullable = false)
    private Integer totalCharged;

    @Column(name = "total_consumed", nullable = false)
    private Integer totalConsumed;

    @Column(name = "pending_minutes", nullable = false)
    private Integer pendingMinutes;

    /**
     * 공부 환율: 30분 = 1 연료.
     */
    public static final int MINUTES_PER_FUEL = 30;

    public static UserFuel initialize(Long userId) {
        return UserFuel.builder()
                .userId(userId)
                .currentFuel(0)
                .totalCharged(0)
                .totalConsumed(0)
                .pendingMinutes(0)
                .build();
    }

    public void charge(int amount) {
        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        this.currentFuel += amount;
        this.totalCharged += amount;
    }

    /**
     * 공부 세션으로부터 연료를 충전한다.
     *
     * 잔여분({@link #pendingMinutes})과 이번 세션의 공부 시간을 합산하여
     * {@value #MINUTES_PER_FUEL}분 단위로 끊어 충전하고, 나머지는 다음 세션을 위해
     * 잔여분으로 이월한다. 정수 연료가 발생할 때만 {@code currentFuel} /
     * {@code totalCharged}가 증가한다.
     *
     * @param studyMinutes 이번 세션의 공부 시간(분), 0 이하 입력은 {@link ErrorCode#INVALID_INPUT_VALUE}
     * @return 충전된 연료 통 수와 갱신된 잔여 분
     */
    public ChargeFromStudyResult chargeFromStudy(int studyMinutes) {
        if (studyMinutes <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int totalMinutes = this.pendingMinutes + studyMinutes;
        int amount = totalMinutes / MINUTES_PER_FUEL;
        int newPending = totalMinutes % MINUTES_PER_FUEL;

        this.pendingMinutes = newPending;
        if (amount > 0) {
            this.currentFuel += amount;
            this.totalCharged += amount;
        }
        return new ChargeFromStudyResult(amount, newPending);
    }

    /**
     * {@link #chargeFromStudy(int)} 결과 — Entity 내부 계산 결과를 서비스에 전달하기 위한 값 객체.
     */
    public record ChargeFromStudyResult(int amount, int newPendingMinutes) {}

    public void consume(int amount) {
        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (this.currentFuel < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_FUEL);
        }
        this.currentFuel -= amount;
        this.totalConsumed += amount;
    }
}
