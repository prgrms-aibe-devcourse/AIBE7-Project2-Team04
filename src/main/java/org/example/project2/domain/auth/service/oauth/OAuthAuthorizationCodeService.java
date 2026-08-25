package org.example.project2.domain.auth.service.oauth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.exception.InvalidOAuthAuthorizationCodeException;
import org.example.project2.domain.auth.service.token.OpaqueTokenGenerator;
import org.example.project2.domain.auth.service.token.RefreshTokenHasher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
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

    public Authorization consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidOAuthAuthorizationCodeException();
        }

        String value = redisTemplate.opsForValue().getAndDelete(key(rawCode));
        return parse(value).orElseThrow(InvalidOAuthAuthorizationCodeException::new);
    }

    private Optional<Authorization> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String[] parts = value.split(":", -1);
        if (parts.length != 2 || !(parts[1].equals("true") || parts[1].equals("false"))) {
            return Optional.empty();
        }

        try {
            return Optional.of(new Authorization(UUID.fromString(parts[0]), Boolean.parseBoolean(parts[1])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String key(String rawCode) {
        return KEY_PREFIX + tokenHasher.hash(rawCode);
    }

    public record Authorization(UUID userId, boolean profileSetupRequired) {
    }
}
