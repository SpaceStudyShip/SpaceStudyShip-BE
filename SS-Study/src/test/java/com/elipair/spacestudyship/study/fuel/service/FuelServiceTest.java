package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepository;
import com.elipair.spacestudyship.study.fuel.repository.UserFuelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FuelServiceTest {

    @Mock UserFuelRepository userFuelRepository;
    @Mock FuelTransactionRepository transactionRepository;
    @InjectMocks FuelService fuelService;

    @Test
    @DisplayName("initialize: 미존재 회원이면 UserFuel.initialize 저장")
    void initialize_newMember_saves() {
        given(userFuelRepository.existsByUserId(1L)).willReturn(false);

        fuelService.initialize(1L);

        ArgumentCaptor<UserFuel> captor = ArgumentCaptor.forClass(UserFuel.class);
        verify(userFuelRepository, times(1)).save(captor.capture());
        UserFuel saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCurrentFuel()).isZero();
        assertThat(saved.getTotalCharged()).isZero();
        assertThat(saved.getTotalConsumed()).isZero();
        assertThat(saved.getPendingMinutes()).isZero();
    }

    @Test
    @DisplayName("initialize: 이미 존재하면 skip (save 호출 없음)")
    void initialize_existing_skips() {
        given(userFuelRepository.existsByUserId(1L)).willReturn(true);

        fuelService.initialize(1L);

        verify(userFuelRepository, never()).save(any());
    }

    @Test
    @DisplayName("getFuel: 존재 시 FuelResponse 반환")
    void getFuel_existing_returnsResponse() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);
        given(userFuelRepository.findByUserId(1L)).willReturn(Optional.of(fuel));

        FuelResponse response = fuelService.getFuel(1L);

        assertThat(response.currentFuel()).isEqualTo(100);
        assertThat(response.totalCharged()).isEqualTo(100);
        assertThat(response.totalConsumed()).isZero();
        assertThat(response.pendingMinutes()).isZero();
    }

    @Test
    @DisplayName("getFuel: 미초기화면 FUEL_NOT_INITIALIZED")
    void getFuel_notInitialized_throws() {
        given(userFuelRepository.findByUserId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fuelService.getFuel(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
    }

    @Test
    @DisplayName("getTransactions: 모든 필터 null 통과")
    void getTransactions_allNulls_passesNullsAndDefaultPageable() {
        given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willAnswer(invocation -> {
                    Pageable p = invocation.getArgument(4);
                    return new PageImpl<>(List.of(), p, 0L);
                });

        FuelTransactionListResponse response = fuelService.getTransactions(
                1L, null, null, null, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @DisplayName("getTransactions: startDate/endDate를 LocalDateTime 반열림 [start, end+1)로 변환")
    void getTransactions_dateRange_convertsToHalfOpen() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        given(transactionRepository.findByFilters(eq(1L), eq(TransactionType.CHARGE),
                startCaptor.capture(), endCaptor.capture(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        fuelService.getTransactions(1L, TransactionType.CHARGE,
                "2026-04-01", "2026-04-16", 0, 20);

        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 1).atStartOfDay());
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 17).atStartOfDay());  // +1일
    }

    @Test
    @DisplayName("getTransactions: Pageable의 정렬은 createdAt DESC 강제")
    void getTransactions_sortIsCreatedAtDesc() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), pageableCaptor.capture()))
                .willReturn(new PageImpl<>(List.of()));

        fuelService.getTransactions(1L, null, null, null, 1, 5);

        Pageable captured = pageableCaptor.getValue();
        Sort.Order order = captured.getSort().getOrderFor("createdAt");
        assertThat(captured.getPageNumber()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(5);
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getTransactions: 내용 매핑 및 envelope 필드 정합")
    void getTransactions_mapsContentCorrectly() {
        FuelTransaction tx = FuelTransaction.of(
                "tx-1", 1L, TransactionType.CHARGE, 90,
                FuelReason.STUDY_SESSION, "s-1", 350);
        given(transactionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(tx), Pageable.unpaged(), 1L));

        FuelTransactionListResponse response = fuelService.getTransactions(
                1L, null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo("tx-1");
        assertThat(response.content().get(0).type()).isEqualTo("charge");
        assertThat(response.content().get(0).reason()).isEqualTo("STUDY_SESSION");
    }

    @Test
    @DisplayName("charge: 정상 흐름 - 락 획득 → entity.charge → tx 저장")
    void charge_happy() {
        UserFuel fuel = UserFuel.initialize(1L);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

        FuelTransactionResponse response = fuelService.charge(
                1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1");

        ArgumentCaptor<FuelTransaction> captor = ArgumentCaptor.forClass(FuelTransaction.class);
        verify(transactionRepository).save(captor.capture());
        FuelTransaction saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("tx-1");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(saved.getAmount()).isEqualTo(90);
        assertThat(saved.getReason()).isEqualTo(FuelReason.STUDY_SESSION);
        assertThat(saved.getReferenceId()).isEqualTo("s-1");
        assertThat(saved.getBalanceAfter()).isEqualTo(90);

        assertThat(response.id()).isEqualTo("tx-1");
        assertThat(response.balanceAfter()).isEqualTo(90);
        assertThat(fuel.getCurrentFuel()).isEqualTo(90);
        assertThat(fuel.getTotalCharged()).isEqualTo(90);
    }

    @Test
    @DisplayName("charge: idempotent - 동일 transactionId 재호출 시 기존 tx 반환, 락/저장 없음")
    void charge_idempotent() {
        FuelTransaction existing = FuelTransaction.of(
                "tx-1", 1L, TransactionType.CHARGE, 90,
                FuelReason.STUDY_SESSION, "s-1", 350);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.of(existing));

        FuelTransactionResponse response = fuelService.charge(
                1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1");

        assertThat(response.id()).isEqualTo("tx-1");
        assertThat(response.balanceAfter()).isEqualTo(350);
        verify(userFuelRepository, never()).findByUserIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("charge: 동일 transactionId지만 다른 userId의 tx 존재 시 INVALID_INPUT_VALUE")
    void charge_idempotentWithDifferentUserId_throws() {
        FuelTransaction otherUserTx = FuelTransaction.of(
                "tx-1", 99L, TransactionType.CHARGE, 90,
                FuelReason.STUDY_SESSION, "s-1", 90);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.of(otherUserTx));

        assertThatThrownBy(() -> fuelService.charge(
                1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userFuelRepository, never()).findByUserIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("charge: amount<=0 시 INVALID_INPUT_VALUE")
    void charge_invalidAmount_throws() {
        assertThatThrownBy(() -> fuelService.charge(
                1L, 0, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> fuelService.charge(
                1L, -10, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("charge: UserFuel 미초기화 시 FUEL_NOT_INITIALIZED")
    void charge_fuelNotInitialized_throws() {
        given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fuelService.charge(
                1L, 90, FuelReason.STUDY_SESSION, "s-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
    }

    @Test
    @DisplayName("consume: 정상 흐름")
    void consume_happy() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(100);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

        FuelTransactionResponse response = fuelService.consume(
                1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1");

        ArgumentCaptor<FuelTransaction> captor = ArgumentCaptor.forClass(FuelTransaction.class);
        verify(transactionRepository).save(captor.capture());
        FuelTransaction saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(TransactionType.CONSUME);
        assertThat(saved.getAmount()).isEqualTo(30);
        assertThat(saved.getReason()).isEqualTo(FuelReason.EXPLORATION_UNLOCK);
        assertThat(saved.getReferenceId()).isEqualTo("region-1");
        assertThat(saved.getBalanceAfter()).isEqualTo(70);

        assertThat(response.balanceAfter()).isEqualTo(70);
        assertThat(fuel.getCurrentFuel()).isEqualTo(70);
        assertThat(fuel.getTotalConsumed()).isEqualTo(30);
    }

    @Test
    @DisplayName("consume: idempotent - 동일 transactionId 재호출 시 no-op")
    void consume_idempotent() {
        FuelTransaction existing = FuelTransaction.of(
                "tx-1", 1L, TransactionType.CONSUME, 30,
                FuelReason.EXPLORATION_UNLOCK, "region-1", 70);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.of(existing));

        FuelTransactionResponse response = fuelService.consume(
                1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1");

        assertThat(response.id()).isEqualTo("tx-1");
        verify(userFuelRepository, never()).findByUserIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume: 동일 transactionId지만 다른 userId의 tx 존재 시 INVALID_INPUT_VALUE")
    void consume_idempotentWithDifferentUserId_throws() {
        FuelTransaction otherUserTx = FuelTransaction.of(
                "tx-1", 99L, TransactionType.CONSUME, 30,
                FuelReason.EXPLORATION_UNLOCK, "region-1", 70);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.of(otherUserTx));

        assertThatThrownBy(() -> fuelService.consume(
                1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userFuelRepository, never()).findByUserIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume: amount<=0 시 INVALID_INPUT_VALUE")
    void consume_invalidAmount_throws() {
        assertThatThrownBy(() -> fuelService.consume(
                1L, 0, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> fuelService.consume(
                1L, -10, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("consume: 잔량 부족 시 INSUFFICIENT_FUEL (Entity 내부 던짐)")
    void consume_insufficient_throws() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(20);
        given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

        assertThatThrownBy(() -> fuelService.consume(
                1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUEL);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume: UserFuel 미초기화 시 FUEL_NOT_INITIALIZED")
    void consume_fuelNotInitialized_throws() {
        given(transactionRepository.findById("tx-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fuelService.consume(
                1L, 30, FuelReason.EXPLORATION_UNLOCK, "region-1", "tx-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
    }

    // ---------- chargeFromStudy (30분 = 1연료, 잔여분 이월) ----------

    @Test
    @DisplayName("chargeFromStudy: 90분 → amount=3, pending=0. fuel_transactions INSERT 1회")
    void chargeFromStudy_90min_charges3() {
        UserFuel fuel = UserFuel.initialize(1L);
        given(transactionRepository.findById("sess-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

        var result = fuelService.chargeFromStudy(1L, 90, "sess-1");

        assertThat(result.amount()).isEqualTo(3);
        assertThat(result.newPendingMinutes()).isZero();
        assertThat(result.currentFuel()).isEqualTo(3);
        assertThat(fuel.getCurrentFuel()).isEqualTo(3);
        assertThat(fuel.getPendingMinutes()).isZero();

        ArgumentCaptor<FuelTransaction> cap = ArgumentCaptor.forClass(FuelTransaction.class);
        verify(transactionRepository).save(cap.capture());
        FuelTransaction tx = cap.getValue();
        assertThat(tx.getId()).isEqualTo("sess-1");
        assertThat(tx.getAmount()).isEqualTo(3);
        assertThat(tx.getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(tx.getReason()).isEqualTo(FuelReason.STUDY_SESSION);
        assertThat(tx.getReferenceId()).isEqualTo("sess-1");
    }

    @Test
    @DisplayName("chargeFromStudy: 25분 → amount=0, pending=25. transaction 미생성 (pending만 누적)")
    void chargeFromStudy_25min_noTransactionPendingOnly() {
        UserFuel fuel = UserFuel.initialize(1L);
        given(transactionRepository.findById("sess-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(fuel));

        var result = fuelService.chargeFromStudy(1L, 25, "sess-1");

        assertThat(result.amount()).isZero();
        assertThat(result.newPendingMinutes()).isEqualTo(25);
        assertThat(result.currentFuel()).isZero();
        assertThat(fuel.getPendingMinutes()).isEqualTo(25);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("chargeFromStudy: 동일 sessionId 재호출 → idempotent skip, fuel 변경 없음")
    void chargeFromStudy_idempotent() {
        UserFuel fuel = UserFuel.initialize(1L);
        fuel.charge(3);   // 사전 상태: 이미 3연료 있음
        FuelTransaction existing = FuelTransaction.of(
                "sess-1", 1L, TransactionType.CHARGE, 3,
                FuelReason.STUDY_SESSION, "sess-1", 3);
        given(transactionRepository.findById("sess-1")).willReturn(Optional.of(existing));
        given(userFuelRepository.findByUserId(1L)).willReturn(Optional.of(fuel));

        var result = fuelService.chargeFromStudy(1L, 90, "sess-1");

        assertThat(result.amount()).isEqualTo(3);
        assertThat(result.currentFuel()).isEqualTo(3);
        verify(userFuelRepository, never()).findByUserIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("chargeFromStudy: studyMinutes<=0 → INVALID_INPUT_VALUE")
    void chargeFromStudy_nonPositive_throws() {
        assertThatThrownBy(() -> fuelService.chargeFromStudy(1L, 0, "sess-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> fuelService.chargeFromStudy(1L, -5, "sess-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("chargeFromStudy: UserFuel 미초기화 시 FUEL_NOT_INITIALIZED")
    void chargeFromStudy_fuelNotInitialized_throws() {
        given(transactionRepository.findById("sess-1")).willReturn(Optional.empty());
        given(userFuelRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fuelService.chargeFromStudy(1L, 60, "sess-1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUEL_NOT_INITIALIZED);
    }

    // ---------- findChargedAmountBySessionId ----------

    @Test
    @DisplayName("findChargedAmountBySessionId: transaction 있으면 amount, 없으면 0")
    void findChargedAmountBySessionId() {
        FuelTransaction tx = FuelTransaction.of(
                "sess-1", 1L, TransactionType.CHARGE, 3,
                FuelReason.STUDY_SESSION, "sess-1", 3);
        given(transactionRepository.findById("sess-1")).willReturn(Optional.of(tx));
        given(transactionRepository.findById("sess-2")).willReturn(Optional.empty());

        assertThat(fuelService.findChargedAmountBySessionId("sess-1")).isEqualTo(3);
        assertThat(fuelService.findChargedAmountBySessionId("sess-2")).isZero();
    }
}
