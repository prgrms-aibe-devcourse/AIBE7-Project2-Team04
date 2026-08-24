package org.example.project2.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final String DEFAULT_ERROR_CODE = "AUTH_001";
    private static final Set<String> CLIENT_SAFE_ERROR_CODES = Set.of("AUTH_004", "AUTH_005");

    private final OAuthProperties properties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String errorCode = resolveClientSafeErrorCode(exception);
        String redirectUri = UriComponentsBuilder
                .fromUriString(properties.successRedirectUri())
                .queryParam("error", errorCode)
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

    private String resolveClientSafeErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();
            if (CLIENT_SAFE_ERROR_CODES.contains(errorCode)) {
                return errorCode;
            }
        }
        return DEFAULT_ERROR_CODE;
    }
}
