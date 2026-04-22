package com.elipair.spacestudyship.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long memberId, String refreshToken, long ttlMillis) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttlMillis));
    }

    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + memberId));
    }

    public void delete(Long memberId) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.delete(key);
    }
}
