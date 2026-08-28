package org.example.project2.domain.matching.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisRealtimeMatchProposalStore implements RealtimeMatchProposalStore {
    private static final String KEY_PREFIX = "match:proposal:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(long proposalId, Duration ttl) {
        redisTemplate.opsForValue().set(key(proposalId), Long.toString(proposalId), ttl);
    }

    @Override
    public void remove(long proposalId) {
        redisTemplate.delete(key(proposalId));
    }

    @Override
    public Optional<Duration> remainingTtl(long proposalId) {
        Long milliseconds = redisTemplate.getExpire(key(proposalId), TimeUnit.MILLISECONDS);
        if (milliseconds == null || milliseconds < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(milliseconds));
    }

    private String key(long proposalId) {
        return KEY_PREFIX + proposalId;
    }
}
