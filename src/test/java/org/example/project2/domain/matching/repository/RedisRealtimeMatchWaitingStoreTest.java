package org.example.project2.domain.matching.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeMatchWaitingStoreTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock ZSetOperations<String, String> zSetOperations;

    private RedisRealtimeMatchWaitingStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisRealtimeMatchWaitingStore(redisTemplate);
    }

    @Test
    void reservesPerUserWithNxAndTtl() {
        when(valueOperations.setIfAbsent("match:user:" + USER_ID, "reservation", Duration.ofMinutes(5)))
                .thenReturn(true);

        assertThat(store.reserve(USER_ID, "reservation", Duration.ofMinutes(5))).isTrue();

        verify(valueOperations).setIfAbsent(
                "match:user:" + USER_ID,
                "reservation",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void activationStoresOnlyIdentifiersAndCoordinatesInAtomicScript() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        assertThat(store.activate(
                USER_ID, "reservation", 42L, 127.039, 37.501, Duration.ofMinutes(5)
        )).isTrue();

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keys.capture(), arguments.capture());

        assertThat(keys.getValue()).containsExactly(
                "match:user:" + USER_ID,
                "match:waiting:42",
                "match:waiting:geo:entry:42",
                "match:waiting:geo"
        );
        assertThat(arguments.getValue()).containsExactly(
                "reservation", "42", USER_ID.toString(), "300000", "127.039", "37.501"
        );
        assertThat(Arrays.stream(arguments.getValue()).map(String::valueOf))
                .noneMatch(value -> value.contains("self-description") || value.contains("jwt"));
    }

    @Test
    void suspendsGeoEntryButKeepsShortUserLockForProposalConfirmation() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        assertThat(store.suspend(USER_ID, 42L, Duration.ofSeconds(15))).isTrue();

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keys.capture(), arguments.capture());
        assertThat(keys.getValue()).containsExactly(
                "match:user:" + USER_ID,
                "match:waiting:42",
                "match:waiting:geo:entry:42",
                "match:waiting:geo"
        );
        assertThat(arguments.getValue()).containsExactly("42", USER_ID.toString(), "15000");
    }

    @Test
    void removesUserRequestAndGeoMemberTogether() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        store.remove(USER_ID, 42L);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keys.capture(), arguments.capture());
        assertThat(keys.getValue()).containsExactly(
                "match:user:" + USER_ID,
                "match:waiting:42",
                "match:waiting:geo:entry:42",
                "match:waiting:geo"
        );
        assertThat(arguments.getValue()).containsExactly("42", USER_ID.toString());
    }

    @Test
    void restoresTwoRequestsAndTheirGeoMembersInOneScript() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);
        UUID secondUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(store.restorePair(
                new RealtimeMatchWaitingEntry(USER_ID, 42L, 127.039, 37.501),
                new RealtimeMatchWaitingEntry(secondUserId, 43L, 127.040, 37.502),
                Duration.ofMinutes(5)
        )).isTrue();

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keys.capture(), arguments.capture());
        assertThat(keys.getValue()).containsExactly(
                "match:user:" + USER_ID,
                "match:waiting:42",
                "match:waiting:geo:entry:42",
                "match:user:" + secondUserId,
                "match:waiting:43",
                "match:waiting:geo:entry:43",
                "match:waiting:geo"
        );
        assertThat(arguments.getValue()).containsExactly(
                "42", USER_ID.toString(), "127.039", "37.501",
                "43", secondUserId.toString(), "127.04", "37.502", "300000"
        );
    }

    @Test
    void readsTtlWithMillisecondPrecision() {
        when(redisTemplate.getExpire("match:waiting:42", TimeUnit.MILLISECONDS)).thenReturn(1_234L);

        assertThat(store.remainingTtl(42L)).contains(Duration.ofMillis(1_234));
        verify(redisTemplate).getExpire("match:waiting:42", TimeUnit.MILLISECONDS);
    }

    @Test
    void treatsMissingRedisKeyAsNoTtl() {
        when(redisTemplate.getExpire(eq("match:waiting:42"), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);

        assertThat(store.remainingTtl(42L)).isEmpty();
    }

    @Test
    void removesGeoMembersWhosePerRequestMarkerExpired() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.range("match:waiting:geo", 0, 999))
                .thenReturn(java.util.Set.of("42", "43"));
        when(redisTemplate.hasKey("match:waiting:geo:entry:42")).thenReturn(true);
        when(redisTemplate.hasKey("match:waiting:geo:entry:43")).thenReturn(false);
        when(zSetOperations.remove("match:waiting:geo", "43")).thenReturn(1L);

        assertThat(store.cleanupExpiredGeoMembers()).isEqualTo(1);
        verify(zSetOperations).remove("match:waiting:geo", "43");
    }
}
