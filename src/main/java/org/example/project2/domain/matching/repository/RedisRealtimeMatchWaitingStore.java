package org.example.project2.domain.matching.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisRealtimeMatchWaitingStore implements RealtimeMatchWaitingStore {
    private static final String USER_KEY_PREFIX = "match:user:";
    private static final String REQUEST_KEY_PREFIX = "match:waiting:";
    private static final DefaultRedisScript<Long> ACTIVATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('psetex', KEYS[1], ARGV[4], ARGV[2])
            redis.call('psetex', KEYS[2], ARGV[4], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_RESERVATION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              redis.call('del', KEYS[1])
            end
            if redis.call('get', KEYS[2]) == ARGV[2] then
              redis.call('del', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean reserve(UUID userId, String reservationToken, Duration ttl) {
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(userKey(userId), reservationToken, ttl);
        return Boolean.TRUE.equals(reserved);
    }

    @Override
    public boolean activate(UUID userId, String reservationToken, long requestId, Duration ttl) {
        Long activated = redisTemplate.execute(
                ACTIVATE_SCRIPT,
                List.of(userKey(userId), requestKey(requestId)),
                reservationToken,
                Long.toString(requestId),
                userId.toString(),
                Long.toString(ttl.toMillis())
        );
        return Long.valueOf(1L).equals(activated);
    }

    @Override
    public void releaseReservation(UUID userId, String reservationToken) {
        redisTemplate.execute(
                RELEASE_RESERVATION_SCRIPT,
                List.of(userKey(userId)),
                reservationToken
        );
    }

    @Override
    public void remove(UUID userId, long requestId) {
        redisTemplate.execute(
                REMOVE_SCRIPT,
                List.of(userKey(userId), requestKey(requestId)),
                Long.toString(requestId),
                userId.toString()
        );
    }

    @Override
    public Optional<Duration> remainingTtl(long requestId) {
        Long seconds = redisTemplate.getExpire(requestKey(requestId), TimeUnit.SECONDS);
        if (seconds == null || seconds < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String requestKey(long requestId) {
        return REQUEST_KEY_PREFIX + requestId;
    }
}
