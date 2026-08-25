package org.example.project2.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.global.security.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void issuedTokenContainsAndValidatesIssuer() {
        JwtProvider provider = jwtProvider("project2");

        String token = provider.issueToken(USER_ID, UserRole.USER);
        Claims claims = provider.parseToken(token);

        assertThat(claims.getIssuer()).isEqualTo("project2");
        assertThat(claims.getAudience()).containsExactly("project2-api");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("roles")).asInstanceOf(LIST).containsExactly("USER");
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
    }

    @Test
    void tokenWithDifferentIssuerIsRejected() {
        String token = jwtProvider("other-project").issueToken(USER_ID, UserRole.USER);

        assertThatThrownBy(() -> jwtProvider("project2").parseToken(token))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void tokenWithDifferentAudienceIsRejected() {
        String token = jwtProvider("project2", "other-api").issueToken(USER_ID, UserRole.USER);

        assertThatThrownBy(() -> jwtProvider("project2").parseToken(token))
                .isInstanceOf(IncorrectClaimException.class);
    }

    private JwtProvider jwtProvider(String issuer) {
        return jwtProvider(issuer, "project2-api");
    }

    private JwtProvider jwtProvider(String issuer, String audience) {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        issuer,
                        audience,
                        TEST_SECRET,
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        5,
                        Duration.ofDays(7)
                ),
                new AuthProperties.Cors("")
        );
        return new JwtProvider(properties);
    }
}
