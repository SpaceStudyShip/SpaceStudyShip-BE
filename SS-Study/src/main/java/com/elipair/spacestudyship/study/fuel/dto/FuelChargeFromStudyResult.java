package com.elipair.spacestudyship.study.fuel.dto;

/**
 * 공부 세션에서 파생된 연료 충전 결과.
 *
 * 환율: 30분 = 1 연료. 30분 미만 잔여분은 {@code newPendingMinutes}에 누적되어
 * 다음 세션과 합산된다.
 *
 * @param amount             이번 호출로 충전된 연료 통 수 (0 이상)
 * @param newPendingMinutes  충전 후 남은 잔여 분 (0~29)
 * @param currentFuel        충전 후의 현재 연료 잔량
 */
public record FuelChargeFromStudyResult(
        int amount,
        int newPendingMinutes,
        int currentFuel
) {}
