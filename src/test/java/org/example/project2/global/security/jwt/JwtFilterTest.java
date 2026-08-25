package org.example.project2.global.security.jwt;

import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.global.security.AuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtFilterTest {
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bearerTokenCreatesAuthenticationWithTokenRole() throws Exception {
        JwtProvider provider = new JwtProvider(properties());
        String token = provider.issueToken(USER_ID, UserRole.ADMIN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        new JwtFilter(provider).doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(USER_ID);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    private AuthProperties properties() {
        return new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        Base64.getEncoder().encodeToString(new byte[32]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        5,
                        Duration.ofDays(7)
                ),
                new AuthProperties.Cors("")
        );
    }
}
