package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행성 해금 응답")
public record PlanetUnlockResponse(
        UnlockedNodeDto planet, int fuelConsumed, int currentFuel
) {
    public static PlanetUnlockResponse of(ExplorationNode planet, UserExploration progress,
                                          int fuelConsumed, int currentFuel) {
        return new PlanetUnlockResponse(
                UnlockedNodeDto.of(planet, progress, false),
                fuelConsumed, currentFuel);
    }
}
