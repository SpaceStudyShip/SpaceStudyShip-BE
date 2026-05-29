package com.elipair.spacestudyship.common.exception;

import lombok.Getter;

@Getter
public class InsufficientFuelException extends RuntimeException {

    private final int requiredFuel;
    private final int currentFuel;

    public InsufficientFuelException(int requiredFuel, int currentFuel) {
        super(ErrorCode.INSUFFICIENT_FUEL.getMessage());
        this.requiredFuel = requiredFuel;
        this.currentFuel = currentFuel;
    }
}
