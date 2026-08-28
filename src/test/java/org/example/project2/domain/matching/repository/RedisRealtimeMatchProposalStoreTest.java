package org.example.project2.domain.matching.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeMatchProposalStoreTest {
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private RedisRealtimeMatchProposalStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisRealtimeMatchProposalStore(redisTemplate);
    }

    @Test
    void storesProposalIdWithTtlOnly() {
        store.put(301L, Duration.ofSeconds(15));

        verify(valueOperations).set("match:proposal:301", "301", Duration.ofSeconds(15));
    }

    @Test
    void removesProposalTtlKey() {
        store.remove(301L);

        verify(redisTemplate).delete("match:proposal:301");
    }

    @Test
    void readsProposalTtlInMilliseconds() {
        when(redisTemplate.getExpire("match:proposal:301", TimeUnit.MILLISECONDS)).thenReturn(1_500L);

        assertThat(store.remainingTtl(301L)).contains(Optional.of(Duration.ofMillis(1_500)).orElseThrow());
    }
}
