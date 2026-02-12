package com.elipair.spacestudyship.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long memberId, String refreshToken, long ttlMillis) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttlMillis));
    }

    public String findByMemberId(Long memberId) {
        String key = KEY_PREFIX + memberId;
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(Long memberId) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.delete(key);
    }
}
