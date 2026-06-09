package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckNicknameRequest(
        @Schema(
                description = "확인할 닉네임. 2~10자, 한글/영문 대소문자/숫자만 허용. 공백·특수문자·이모지 불가.",
                example = "우주탐험가",
                minLength = 2,
                maxLength = 10
        )
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
        String nickname
) {}
