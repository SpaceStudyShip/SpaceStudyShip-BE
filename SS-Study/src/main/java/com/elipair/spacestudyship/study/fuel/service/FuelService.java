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
     *
     * <h3>Idempotency 계약</h3>
     * Idempotency 키는 {@code sessionId} (= {@link FuelTransaction#getId()}).
     * <ul>
     *   <li><b>amount &gt; 0 (30분 이상 합산)</b>: 동일 sessionId 재호출 시 기존
     *       {@code fuel_transactions} row를 조회해 idempotent skip한다.
     *       잔량/pending 변경 없음.</li>
     *   <li><b>amount = 0 (30분 미만 합산)</b>: {@code fuel_transactions} INSERT가 발생하지
     *       않으므로 fuel-side에서 idempotency를 보장하지 못한다.
     *       <b>호출자는 sessionId의 유일성을 직접 보장해야 한다</b> (예: timer-side의
     *       {@code Idempotency-Key} 헤더 기반 dedup + 매 호출 신규 UUID 생성).
     *       동일 sessionId 재호출 시 {@code pendingMinutes}가 중복 누적된다.</li>
     * </ul>
     *
     * 현재 단일 호출자({@code TimerSessionService.create})가 매 호출마다 신규 UUID를 생성하므로
     * 위 amount=0 시나리오는 발생하지 않는다. 향후 직접 호출자를 추가할 때는 이 계약을 확인하라.
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

        // amount=0이면 transaction을 만들지 않는다 — pending만 갱신.
        // fuel_transactions의 chk_fuel_tx_amount_positive 제약으로 amount=0 INSERT 불가능하기도 하고,
        // 거래 내역(GET /api/fuel/transactions)에 0연료 노이즈 row 노출을 피하기 위함.
        // 대신 sessionId 유일성은 호출자(TimerSessionService)가 보장해야 한다. (위 Javadoc 참조)
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
