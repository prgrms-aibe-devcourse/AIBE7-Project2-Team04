package org.example.project2.global.security.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.service.oauth.OAuthAuthorizationCodeService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthAuthorizationCodeService authorizationCodeService;
    private final OAuthProperties properties;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof KakaoOAuth2User kakaoUser)) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(
                            new OAuth2Error("unsupported_provider"),
                            "현재 카카오 OAuth 로그인만 지원합니다."
                    )
            );
            return;
        }

        String code = authorizationCodeService.issue(
                kakaoUser.getUserId(),
                kakaoUser.isProfileSetupRequired()
        );
        String redirectUri = UriComponentsBuilder
                .fromUriString(properties.successRedirectUri())
                .queryParam("code", code)
                .build()
                .encode()
                .toUriString();

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(redirectUri);
    }
}
