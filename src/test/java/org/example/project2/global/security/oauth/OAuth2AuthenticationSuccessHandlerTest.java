package org.example.project2.global.security.oauth;

import org.example.project2.domain.auth.service.oauth.OAuthAuthorizationCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {
    @Mock
    private OAuthAuthorizationCodeService authorizationCodeService;

    @Mock
    private OAuth2AuthenticationFailureHandler failureHandler;

    @Mock
    private KakaoOAuth2User kakaoUser;

    @Test
    void redirectsWithOneTimeCodeAndRemovesTemporarySession() throws Exception {
        UUID userId = UUID.randomUUID();
        when(kakaoUser.getUserId()).thenReturn(userId);
        when(kakaoUser.isProfileSetupRequired()).thenReturn(true);
        when(authorizationCodeService.issue(userId, true)).thenReturn("one-time-code");
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                authorizationCodeService,
                new OAuthProperties("https://frontend.example/oauth/callback"),
                failureHandler
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(kakaoUser, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/oauth/callback?code=one-time-code");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
