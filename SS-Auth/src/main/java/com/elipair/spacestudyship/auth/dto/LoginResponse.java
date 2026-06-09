package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 응답. 기존 회원이면 200, 신규 회원이면 201로 응답됩니다.")
public record LoginResponse(
        @Schema(description = "서버에서 부여한 회원 ID.", example = "1")
        Long memberId,

        @Schema(
                description = "회원 닉네임. 신규 가입 시 서버가 랜덤 생성(형용사+명사+숫자 4자리 패턴).",
                example = "민첩한괴도5308"
        )
        String nickname,

        @Schema(description = "JWT Access/Refresh Token 쌍.")
        Tokens tokens,

        @Schema(
                description = "신규 가입 여부. true → 닉네임 설정 화면으로 이동 권장. false → 기존 회원, 홈 화면으로 이동.",
                example = "false"
        )
        boolean isNewMember
) {}
