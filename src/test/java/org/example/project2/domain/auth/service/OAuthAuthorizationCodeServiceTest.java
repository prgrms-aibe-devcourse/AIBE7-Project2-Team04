package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.exception.InvalidOAuthAuthorizationCodeException;
import org.example.project2.domain.auth.service.oauth.OAuthAuthorizationCodeService;
import org.example.project2.domain.auth.service.token.OpaqueTokenGenerator;
import org.example.project2.domain.auth.service.token.RefreshTokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAuthorizationCodeServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuthAuthorizationCodeService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new OAuthAuthorizationCodeService(
                redisTemplate,
                new OpaqueTokenGenerator(),
                new RefreshTokenHasher()
        );
    }

    @Test
    void storesOnlyHashedCodeWithShortExpiry() {
        UUID userId = UUID.randomUUID();

        String rawCode = service.issue(userId, true);

        assertThat(rawCode).hasSize(43);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.startsWith("auth:oauth2:code:"),
                eq(userId + ":true"),
                eq(Duration.ofMinutes(2))
        );
    }

    @Test
    void consumesStoredCodeAtomically() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.getAndDelete(startsWith("auth:oauth2:code:")))
                .thenReturn(userId + ":true");

        OAuthAuthorizationCodeService.Authorization authorization = service.consume("raw-code");

        assertThat(authorization.userId()).isEqualTo(userId);
        assertThat(authorization.profileSetupRequired()).isTrue();
        verify(valueOperations).getAndDelete(startsWith("auth:oauth2:code:"));
    }

    @Test
    void rejectsExpiredOrAlreadyConsumedCode() {
        when(valueOperations.getAndDelete(startsWith("auth:oauth2:code:"))).thenReturn(null);

        assertThatThrownBy(() -> service.consume("expired-code"))
                .isInstanceOf(InvalidOAuthAuthorizationCodeException.class);
    }
}
