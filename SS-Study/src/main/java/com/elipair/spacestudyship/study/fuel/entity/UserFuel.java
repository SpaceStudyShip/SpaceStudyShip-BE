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

    public void consume(int amount) {
        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (this.currentFuel < amount) {
            throw new CustomException(ErrorCode.INSUFFICIENT_FUEL);
        }
        this.currentFuel -= amount;
        this.totalConsumed += amount;
    }
}
