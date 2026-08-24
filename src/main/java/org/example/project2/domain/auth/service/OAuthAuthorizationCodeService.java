package org.example.project2.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthAuthorizationCodeService {
    private static final String KEY_PREFIX = "auth:oauth2:code:";
    private static final Duration CODE_EXPIRY = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final OpaqueTokenGenerator tokenGenerator;
    private final RefreshTokenHasher tokenHasher;

    public String issue(UUID userId, boolean profileSetupRequired) {
        String rawCode = tokenGenerator.generate();
        String key = key(rawCode);
        String value = userId + ":" + profileSetupRequired;
        redisTemplate.opsForValue().set(key, value, CODE_EXPIRY);
        return rawCode;
    }

    private String key(String rawCode) {
        return KEY_PREFIX + tokenHasher.hash(rawCode);
    }
}
