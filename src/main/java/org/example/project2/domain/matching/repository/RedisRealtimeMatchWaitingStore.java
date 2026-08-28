package org.example.project2.domain.matching.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisRealtimeMatchWaitingStore implements RealtimeMatchWaitingStore {
    private static final String USER_KEY_PREFIX = "match:user:";
    private static final String REQUEST_KEY_PREFIX = "match:waiting:";
    private static final String GEO_KEY = "match:waiting:geo";
    private static final String GEO_MARKER_KEY_PREFIX = "match:waiting:geo:entry:";
    private static final DefaultRedisScript<Long> ACTIVATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('psetex', KEYS[1], ARGV[4], ARGV[2])
            redis.call('psetex', KEYS[2], ARGV[4], ARGV[3])
            redis.call('psetex', KEYS[3], ARGV[4], ARGV[2])
            redis.call('geoadd', KEYS[4], ARGV[5], ARGV[6], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RESTORE_SCRIPT = new DefaultRedisScript<>("""
            local currentUserRequest = redis.call('get', KEYS[1])
            if currentUserRequest and currentUserRequest ~= ARGV[1] then
              return 0
            end
            local currentRequestUser = redis.call('get', KEYS[2])
            if currentRequestUser and currentRequestUser ~= ARGV[2] then
              return 0
            end
            redis.call('psetex', KEYS[1], ARGV[3], ARGV[1])
            redis.call('psetex', KEYS[2], ARGV[3], ARGV[2])
            redis.call('psetex', KEYS[3], ARGV[3], ARGV[1])
            redis.call('geoadd', KEYS[4], ARGV[4], ARGV[5], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> SUSPEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('psetex', KEYS[1], ARGV[3], ARGV[1])
            if redis.call('get', KEYS[2]) == ARGV[2] then
              redis.call('del', KEYS[2])
              redis.call('del', KEYS[3])
              redis.call('zrem', KEYS[4], ARGV[1])
            elseif redis.call('get', KEYS[3]) == ARGV[1] then
              redis.call('del', KEYS[3])
              redis.call('zrem', KEYS[4], ARGV[1])
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RESTORE_PAIR_SCRIPT = new DefaultRedisScript<>("""
            local firstUserRequest = redis.call('get', KEYS[1])
            if firstUserRequest and firstUserRequest ~= ARGV[1] then
              return 0
            end
            local firstRequestUser = redis.call('get', KEYS[2])
            if firstRequestUser and firstRequestUser ~= ARGV[2] then
              return 0
            end
            local secondUserRequest = redis.call('get', KEYS[4])
            if secondUserRequest and secondUserRequest ~= ARGV[5] then
              return 0
            end
            local secondRequestUser = redis.call('get', KEYS[5])
            if secondRequestUser and secondRequestUser ~= ARGV[6] then
              return 0
            end
            redis.call('psetex', KEYS[1], ARGV[9], ARGV[1])
            redis.call('psetex', KEYS[2], ARGV[9], ARGV[2])
            redis.call('psetex', KEYS[3], ARGV[9], ARGV[1])
            redis.call('psetex', KEYS[4], ARGV[9], ARGV[5])
            redis.call('psetex', KEYS[5], ARGV[9], ARGV[6])
            redis.call('psetex', KEYS[6], ARGV[9], ARGV[5])
            redis.call('geoadd', KEYS[7], ARGV[3], ARGV[4], ARGV[1], ARGV[7], ARGV[8], ARGV[5])
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
              redis.call('del', KEYS[3])
              redis.call('zrem', KEYS[4], ARGV[1])
            elseif redis.call('get', KEYS[3]) == ARGV[1] then
              redis.call('del', KEYS[3])
              redis.call('zrem', KEYS[4], ARGV[1])
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
    public boolean activate(
            UUID userId,
            String reservationToken,
            long requestId,
            double longitude,
            double latitude,
            Duration ttl
    ) {
        Long activated = redisTemplate.execute(
                ACTIVATE_SCRIPT,
                List.of(userKey(userId), requestKey(requestId), geoMarkerKey(requestId), GEO_KEY),
                reservationToken,
                Long.toString(requestId),
                userId.toString(),
                Long.toString(ttl.toMillis()),
                Double.toString(longitude),
                Double.toString(latitude)
        );
        return Long.valueOf(1L).equals(activated);
    }

    @Override
    public boolean suspend(UUID userId, long requestId, Duration proposalTtl) {
        Long suspended = redisTemplate.execute(
                SUSPEND_SCRIPT,
                List.of(userKey(userId), requestKey(requestId), geoMarkerKey(requestId), GEO_KEY),
                Long.toString(requestId),
                userId.toString(),
                Long.toString(proposalTtl.toMillis())
        );
        return Long.valueOf(1L).equals(suspended);
    }

    @Override
    public boolean restore(UUID userId, long requestId, double longitude, double latitude, Duration ttl) {
        Long restored = redisTemplate.execute(
                RESTORE_SCRIPT,
                List.of(userKey(userId), requestKey(requestId), geoMarkerKey(requestId), GEO_KEY),
                Long.toString(requestId),
                userId.toString(),
                Long.toString(ttl.toMillis()),
                Double.toString(longitude),
                Double.toString(latitude)
        );
        return Long.valueOf(1L).equals(restored);
    }

    @Override
    public boolean restorePair(
            RealtimeMatchWaitingEntry first,
            RealtimeMatchWaitingEntry second,
            Duration ttl
    ) {
        Long restored = redisTemplate.execute(
                RESTORE_PAIR_SCRIPT,
                List.of(
                        userKey(first.userId()),
                        requestKey(first.requestId()),
                        geoMarkerKey(first.requestId()),
                        userKey(second.userId()),
                        requestKey(second.requestId()),
                        geoMarkerKey(second.requestId()),
                        GEO_KEY
                ),
                Long.toString(first.requestId()),
                first.userId().toString(),
                Double.toString(first.longitude()),
                Double.toString(first.latitude()),
                Long.toString(second.requestId()),
                second.userId().toString(),
                Double.toString(second.longitude()),
                Double.toString(second.latitude()),
                Long.toString(ttl.toMillis())
        );
        return Long.valueOf(1L).equals(restored);
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
                List.of(userKey(userId), requestKey(requestId), geoMarkerKey(requestId), GEO_KEY),
                Long.toString(requestId),
                userId.toString()
        );
    }

    @Override
    public int cleanupExpiredGeoMembers() {
        Set<String> members = redisTemplate.opsForZSet().range(GEO_KEY, 0, 999);
        if (members == null || members.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String member : members) {
            try {
                if (!redisTemplate.hasKey(geoMarkerKey(Long.parseLong(member)))) {
                    Long count = redisTemplate.opsForZSet().remove(GEO_KEY, member);
                    removed += count == null ? 0 : count.intValue();
                }
            } catch (NumberFormatException ignored) {
                Long count = redisTemplate.opsForZSet().remove(GEO_KEY, member);
                removed += count == null ? 0 : count.intValue();
            }
        }
        return removed;
    }

    @Override
    public Optional<Duration> remainingTtl(long requestId) {
        Long milliseconds = redisTemplate.getExpire(requestKey(requestId), TimeUnit.MILLISECONDS);
        if (milliseconds == null || milliseconds < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(milliseconds));
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String requestKey(long requestId) {
        return REQUEST_KEY_PREFIX + requestId;
    }

    private String geoMarkerKey(long requestId) {
        return GEO_MARKER_KEY_PREFIX + requestId;
    }
}
