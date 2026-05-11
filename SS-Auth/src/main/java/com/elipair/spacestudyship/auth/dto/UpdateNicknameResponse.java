package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 변경 응답. 변경된 닉네임을 반환합니다.")
public record UpdateNicknameResponse(
        @Schema(description = "변경된 회원 닉네임.", example = "우주탐험가")
        String nickname
) {}
