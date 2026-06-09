package com.elipair.spacestudyship.study.exploration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행성 진행도")
public record ProgressDto(
        @Schema(example = "3") int clearedChildren,
        @Schema(example = "5") int totalChildren,
        @Schema(example = "0.6") double progressRatio
) {}
