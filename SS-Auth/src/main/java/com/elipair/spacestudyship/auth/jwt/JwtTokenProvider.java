package com.elipair.spacestudyship.auth.jwt;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.entity.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.access().secret()));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.refresh().secret()));
    }

    // ===== Access Token =====

    public String createAccessToken(Member member) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.access().expiration().toMillis());

        return Jwts.builder()
                .subject(member.getId().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(accessKey)
                .compact();
    }

    public Long getMemberIdFromAccessToken(String accessToken) {
        return Long.valueOf(getAccessClaims(accessToken).getSubject());
    }

    private Claims getAccessClaims(String accessToken) {
        try {
            return Jwts.parser()
                    .verifyWith(accessKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    // ===== Refresh Token =====

    public String createRefreshToken(Member member) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.refresh().expiration().toMillis());

        return Jwts.builder()
                .subject(member.getId().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(refreshKey)
                .compact();
    }

    public Long getMemberIdFromRefreshToken(String refreshToken) {
        return Long.valueOf(getRefreshClaims(refreshToken).getSubject());
    }

    private Claims getRefreshClaims(String refreshToken) {
        try {
            return Jwts.parser()
                    .verifyWith(refreshKey)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHENTICATED_REQUEST);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 로그아웃 시 사용 - 만료된 토큰에서도 memberId 추출 시도
     */
    public Optional<Long> getMemberIdFromRefreshTokenSafely(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(refreshKey)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (ExpiredJwtException e) {
            return Optional.of(Long.valueOf(e.getClaims().getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getRefreshTokenExpirationMillis() {
        return jwtProperties.refresh().expiration().toMillis();
    }
}
