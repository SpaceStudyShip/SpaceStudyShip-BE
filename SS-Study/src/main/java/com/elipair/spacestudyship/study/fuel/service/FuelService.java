package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelChargeFromStudyResult;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import com.elipair.spacestudyship.study.fuel.repository.FuelTransactionRepository;
import com.elipair.spacestudyship.study.fuel.repository.UserFuelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FuelService {

    private final UserFuelRepository userFuelRepository;
    private final FuelTransactionRepository transactionRepository;

    public FuelResponse getFuel(Long userId) {
        UserFuel fuel = userFuelRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
        return FuelResponse.from(fuel);
    }

    public FuelTransactionListResponse getTransactions(
            Long userId, TransactionType type,
            String startDate, String endDate,
            int page, int size) {

        LocalDateTime startDateTime = startDate == null ? null
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime endDateTime = endDate == null ? null
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<FuelTransaction> result = transactionRepository
                .findByFilters(userId, type, startDateTime, endDateTime, pageable);

        return FuelTransactionListResponse.from(result);
    }

    @Transactional
    public FuelTransactionResponse charge(
            Long userId, int amount, FuelReason reason,
            String referenceId, String transactionId) {

        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        Optional<FuelTransaction> existing = transactionRepository.findById(transactionId);
        if (existing.isPresent()) {
            return idempotentReturn(existing.get(), userId, "charge", transactionId);
        }

        UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));

        // 락 획득 사이에 다른 트랜잭션이 동일 transactionId로 먼저 save한 경우의 경쟁 조건 방어
        Optional<FuelTransaction> raced = transactionRepository.findById(transactionId);
        if (raced.isPresent()) {
            return idempotentReturn(raced.get(), userId, "charge", transactionId);
        }

        fuel.charge(amount);

        FuelTransaction tx = FuelTransaction.of(
                transactionId, userId, TransactionType.CHARGE,
                amount, reason, referenceId, fuel.getCurrentFuel());
        transactionRepository.save(tx);

        log.info("[Fuel] 충전 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
                userId, amount, reason, transactionId, fuel.getCurrentFuel());
        return FuelTransactionResponse.from(tx);
    }

    private FuelTransactionResponse idempotentReturn(FuelTransaction tx, Long userId,
                                                     String action, String transactionId) {
        if (!tx.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        log.info("[Fuel] {} idempotent skip | userId={}, txId={}", action, userId, transactionId);
        return FuelTransactionResponse.from(tx);
    }

    @Transactional
    public FuelTransactionResponse consume(
            Long userId, int amount, FuelReason reason,
            String referenceId, String transactionId) {

        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        Optional<FuelTransaction> existing = transactionRepository.findById(transactionId);
        if (existing.isPresent()) {
            return idempotentReturn(existing.get(), userId, "consume", transactionId);
        }

        UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));

        Optional<FuelTransaction> raced = transactionRepository.findById(transactionId);
        if (raced.isPresent()) {
            return idempotentReturn(raced.get(), userId, "consume", transactionId);
        }

        fuel.consume(amount);

        FuelTransaction tx = FuelTransaction.of(
                transactionId, userId, TransactionType.CONSUME,
                amount, reason, referenceId, fuel.getCurrentFuel());
        transactionRepository.save(tx);

        log.info("[Fuel] 소비 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
                userId, amount, reason, transactionId, fuel.getCurrentFuel());
        return FuelTransactionResponse.from(tx);
    }

    /**
     * 타이머 세션 완료로 인한 연료 충전.
     *
     * 환율: 30분 = 1 연료 (잔여분은 {@link UserFuel#pendingMinutes}로 이월).
     * idempotency 키는 {@code sessionId} (= {@link FuelTransaction#getId()}). 동일 sessionId
     * 재호출 시 기존 transaction을 그대로 반환하고 잔량은 변경하지 않는다.
     *
     * 30분 미만이라 충전 통 수가 0이 나오면 {@code fuel_transactions} INSERT는 건너뛰고
     * {@code user_fuel.pendingMinutes}만 갱신한다 (transaction-less pending 누적).
     */
    @Transactional
    public FuelChargeFromStudyResult chargeFromStudy(
            Long userId, int studyMinutes, String sessionId) {

        if (studyMinutes <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        Optional<FuelTransaction> existing = transactionRepository.findById(sessionId);
        if (existing.isPresent()) {
            return idempotentReturnFromStudy(existing.get(), userId, sessionId);
        }

        UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));

        // 락 획득 후 race 재확인 (다른 트랜잭션이 동일 sessionId로 먼저 INSERT했을 가능성)
        Optional<FuelTransaction> raced = transactionRepository.findById(sessionId);
        if (raced.isPresent()) {
            return idempotentReturnFromStudy(raced.get(), userId, sessionId);
        }

        UserFuel.ChargeFromStudyResult result = fuel.chargeFromStudy(studyMinutes);

        if (result.amount() > 0) {
            FuelTransaction tx = FuelTransaction.of(
                    sessionId, userId, TransactionType.CHARGE,
                    result.amount(), FuelReason.STUDY_SESSION,
                    sessionId, fuel.getCurrentFuel());
            transactionRepository.save(tx);
        }

        log.info("[Fuel] 공부 세션 충전 | userId={}, studyMinutes={}, amount={}, " +
                        "pendingMinutes={}, balanceAfter={}, sessionId={}",
                userId, studyMinutes, result.amount(),
                result.newPendingMinutes(), fuel.getCurrentFuel(), sessionId);
        return new FuelChargeFromStudyResult(
                result.amount(), result.newPendingMinutes(), fuel.getCurrentFuel());
    }

    private FuelChargeFromStudyResult idempotentReturnFromStudy(
            FuelTransaction tx, Long userId, String sessionId) {
        if (!tx.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        UserFuel fuel = userFuelRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
        log.info("[Fuel] 공부 세션 idempotent skip | userId={}, sessionId={}", userId, sessionId);
        return new FuelChargeFromStudyResult(
                tx.getAmount(), fuel.getPendingMinutes(), fuel.getCurrentFuel());
    }

    /**
     * 특정 sessionId에 대응하는 충전 transaction의 amount를 조회한다.
     * Timer 도메인이 dedup 응답을 만들 때 사용 (transaction이 없으면 0).
     */
    public int findChargedAmountBySessionId(String sessionId) {
        return transactionRepository.findById(sessionId)
                .map(FuelTransaction::getAmount)
                .orElse(0);
    }

    @Transactional
    public void initialize(Long userId) {
        if (userFuelRepository.existsByUserId(userId)) {
            log.info("[Fuel] 초기화 스킵 (이미 존재) | userId={}", userId);
            return;
        }
        userFuelRepository.save(UserFuel.initialize(userId));
        log.info("[Fuel] 초기화 | userId={}", userId);
    }
}
