package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지역 해금 응답")
public record RegionUnlockResponse(
        UnlockedNodeDto region, int fuelConsumed, int currentFuel, boolean planetCleared
) {
    public static RegionUnlockResponse of(ExplorationNode region, UserExploration progress,
                                          int fuelConsumed, int currentFuel, boolean planetCleared) {
        return new RegionUnlockResponse(
                UnlockedNodeDto.of(region, progress, true),
                fuelConsumed, currentFuel, planetCleared);
    }
}
