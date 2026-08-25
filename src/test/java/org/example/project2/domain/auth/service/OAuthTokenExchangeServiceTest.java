package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.dto.OAuthTokenExchangeResponse;
import org.example.project2.domain.auth.service.oauth.OAuthAuthorizationCodeService;
import org.example.project2.domain.auth.service.oauth.OAuthTokenExchangeService;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.security.AuthProperties;
import org.example.project2.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthTokenExchangeServiceTest {
    @Mock
    private OAuthAuthorizationCodeService authorizationCodeService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;

    private OAuthTokenExchangeService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Password("argon2"),
                new AuthProperties.Jwt(
                        "project2",
                        "project2-api",
                        Base64.getEncoder().encodeToString(new byte[32]),
                        Duration.ofMinutes(15),
                        Duration.ofDays(14)
                ),
                new AuthProperties.Cors("")
        );
        service = new OAuthTokenExchangeService(
                authorizationCodeService,
                userRepository,
                jwtProvider,
                refreshTokenService,
                properties
        );
    }

    @Test
    void exchangesCodeForAccessAndRefreshTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("oauth@test.com")
                .provider(AuthProvider.KAKAO)
                .providerId("12345")
                .nickname("사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(authorizationCodeService.consume("one-time-code"))
                .thenReturn(new OAuthAuthorizationCodeService.Authorization(userId, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.issueToken(userId, UserRole.USER)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn(
                new RefreshTokenService.IssuedRefreshToken("refresh-token", Instant.now().plusSeconds(60))
        );

        OAuthTokenExchangeService.ExchangeResult result = service.exchange("one-time-code");
        OAuthTokenExchangeResponse response = result.response();

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.profileSetupRequired()).isTrue();
        assertThat(response.toString()).doesNotContain("access-token");
        assertThat(result.rawRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.toString()).doesNotContain("access-token");
        assertThat(result.toString()).doesNotContain("refresh-token");
        verify(authorizationCodeService).consume("one-time-code");
    }
}
