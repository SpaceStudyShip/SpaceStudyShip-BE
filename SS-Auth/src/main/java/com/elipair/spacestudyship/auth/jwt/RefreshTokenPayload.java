package com.elipair.spacestudyship.auth.jwt;

public record RefreshTokenPayload(Long memberId, String deviceId) {}
