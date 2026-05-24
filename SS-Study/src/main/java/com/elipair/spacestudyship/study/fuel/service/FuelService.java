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
            FuelTransaction tx = existing.get();
            if (!tx.getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            log.info("[Fuel] charge idempotent skip | userId={}, txId={}", userId, transactionId);
            return FuelTransactionResponse.from(tx);
        }

        UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
        fuel.charge(amount);

        FuelTransaction tx = FuelTransaction.of(
                transactionId, userId, TransactionType.CHARGE,
                amount, reason, referenceId, fuel.getCurrentFuel());
        transactionRepository.save(tx);

        log.info("[Fuel] 충전 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
                userId, amount, reason, transactionId, fuel.getCurrentFuel());
        return FuelTransactionResponse.from(tx);
    }

    @Transactional
    public FuelTransactionResponse consume(
            Long userId, int amount, FuelReason reason,
            String referenceId, String transactionId) {

        if (amount <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        Optional<FuelTransaction> existing = transactionRepository.findById(transactionId);
        if (existing.isPresent()) {
            FuelTransaction tx = existing.get();
            if (!tx.getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            log.info("[Fuel] consume idempotent skip | userId={}, txId={}", userId, transactionId);
            return FuelTransactionResponse.from(tx);
        }

        UserFuel fuel = userFuelRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.FUEL_NOT_INITIALIZED));
        fuel.consume(amount);

        FuelTransaction tx = FuelTransaction.of(
                transactionId, userId, TransactionType.CONSUME,
                amount, reason, referenceId, fuel.getCurrentFuel());
        transactionRepository.save(tx);

        log.info("[Fuel] 소비 | userId={}, amount={}, reason={}, txId={}, balanceAfter={}",
                userId, amount, reason, transactionId, fuel.getCurrentFuel());
        return FuelTransactionResponse.from(tx);
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
