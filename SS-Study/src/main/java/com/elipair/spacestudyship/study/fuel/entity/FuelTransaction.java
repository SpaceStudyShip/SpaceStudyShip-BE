package com.elipair.spacestudyship.study.fuel.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        @Check(name = "chk_fuel_tx_amount_positive", constraints = "amount > 0"),
        @Check(name = "chk_fuel_tx_type", constraints = "type IN ('CHARGE','CONSUME')"),
        @Check(name = "chk_fuel_tx_reason", constraints = "reason IN ('STUDY_SESSION','EXPLORATION_UNLOCK')")
})
@Table(name = "fuel_transactions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FuelTransaction extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuelReason reason;

    @Column(name = "reference_id", length = 50)
    private String referenceId;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    public static FuelTransaction of(String id, Long userId, TransactionType type,
                                     int amount, FuelReason reason,
                                     String referenceId, int balanceAfter) {
        return FuelTransaction.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .amount(amount)
                .reason(reason)
                .referenceId(referenceId)
                .balanceAfter(balanceAfter)
                .build();
    }
}
