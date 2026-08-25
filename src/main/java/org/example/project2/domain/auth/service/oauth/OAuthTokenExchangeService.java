package org.example.project2.domain.auth.service.oauth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeResponse;
import org.example.project2.domain.auth.exception.InvalidOAuthAuthorizationCodeException;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.security.AuthProperties;
import org.example.project2.global.security.jwt.JwtProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthTokenExchangeService {
    private final OAuthAuthorizationCodeService authorizationCodeService;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    @Transactional
    public ExchangeResult exchange(String code) {
        OAuthAuthorizationCodeService.Authorization authorization = authorizationCodeService.consume(code);
        User user = userRepository.findById(authorization.userId())
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(InvalidOAuthAuthorizationCodeException::new);

        String accessToken = jwtProvider.issueToken(user.getId(), user.getRole());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        OAuthTokenExchangeResponse response = new OAuthTokenExchangeResponse(
                "Bearer",
                accessToken,
                authProperties.jwt().accessTokenExpiry().toSeconds(),
                authorization.profileSetupRequired()
        );

        return new ExchangeResult(response, refreshToken.rawToken());
    }

    public record ExchangeResult(OAuthTokenExchangeResponse response, String rawRefreshToken) {
        @Override
        public String toString() {
            return "ExchangeResult[response=" + response + ", rawRefreshToken=[REDACTED]]";
        }
    }
}
