package com.elipair.spacestudyship.auth.controller.dto;

import com.elipair.spacestudyship.auth.domain.Tokens;

public record ReissueResponse(
        Tokens tokens
) {
    public static ReissueResponse from(Tokens tokens) {
        return new ReissueResponse(tokens);
    }
}
