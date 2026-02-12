package com.elipair.spacestudyship.auth.domain;

public record Tokens(
        String accessToken,
        String refreshToken
) {
}
