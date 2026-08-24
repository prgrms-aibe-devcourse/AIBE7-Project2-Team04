package org.example.project2.global.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthenticationFailureHandlerTest {
    private final OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(
            new OAuthProperties("https://frontend.example/oauth/callback")
    );

    @Test
    void redirectsDocumentedMappingErrorAndRemovesTemporarySession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("AUTH_005"))
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/oauth/callback?error=AUTH_005");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void hidesUndocumentedProviderErrorDetails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new OAuth2AuthenticationException(new OAuth2Error("provider_internal_error"))
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/oauth/callback?error=AUTH_001");
    }
}
