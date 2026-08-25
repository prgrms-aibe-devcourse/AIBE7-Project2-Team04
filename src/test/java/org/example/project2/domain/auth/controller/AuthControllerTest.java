package org.example.project2.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project2.domain.auth.dto.SignUpRequest;
import org.example.project2.domain.auth.dto.SignUpResponse;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeRequest;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeResponse;
import org.example.project2.domain.auth.service.local.AuthService;
import org.example.project2.domain.auth.service.oauth.OAuthTokenExchangeService;
import org.example.project2.global.security.SecurityConfig;
import org.example.project2.global.security.csrf.CsrfCookieFilter;
import org.example.project2.global.security.csrf.SpaCsrfTokenRequestHandler;
import org.example.project2.global.security.auth.CustomUserDetailsService;
import org.example.project2.global.security.handler.RestAccessDeniedHandler;
import org.example.project2.global.security.handler.RestAuthenticationEntryPoint;
import org.example.project2.global.security.jwt.JwtFilter;
import org.example.project2.global.security.oauth.CustomOAuth2UserService;
import org.example.project2.global.security.oauth.OAuth2AuthenticationFailureHandler;
import org.example.project2.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import org.example.project2.global.security.jwt.JwtProvider;
import org.example.project2.global.security.jwt.AuthCookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "app.auth.password.encoding-id=argon2",
                "app.auth.jwt.issuer=project2",
                "app.auth.jwt.audience=project2-api",
                "app.auth.jwt.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "app.auth.jwt.access-token-expiry=15m",
                "app.auth.jwt.refresh-token-expiry=14d",
                "app.auth.cors.allowed-origin=https://frontend.example"
        }
)
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        JwtProvider.class,
        CsrfCookieFilter.class,
        SpaCsrfTokenRequestHandler.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OAuthTokenExchangeService oauthTokenExchangeService;

    @MockitoBean
    private AuthCookieUtil authCookieUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Test
    void signUpSuccess() throws Exception {
        // given
        SignUpRequest request = new SignUpRequest("user@test.com", "password123", "혼밥탈출");
        SignUpResponse response = new SignUpResponse(UUID.randomUUID(), "user@test.com", "혼밥탈출");
        when(authService.signUp(any(SignUpRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("혼밥탈출"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void signUpValidationFails() throws Exception {
        // given
        SignUpRequest request = new SignUpRequest("invalid-email", "short", "");

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_002"));
    }

    @Test
    void oauthCodeExchangeReturnsAccessTokenAndRefreshTokenCookie() throws Exception {
        OAuthTokenExchangeRequest request = new OAuthTokenExchangeRequest("one-time-code");
        OAuthTokenExchangeResponse response = new OAuthTokenExchangeResponse(
                "Bearer", "access-token", 900, true
        );
        when(oauthTokenExchangeService.exchange("one-time-code"))
                .thenReturn(new OAuthTokenExchangeService.ExchangeResult(response, "refresh-token"));
        when(authCookieUtil.createRefreshTokenCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refreshToken", "refresh-token")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path("/auth")
                        .build());

        mockMvc.perform(post("/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.profileSetupRequired").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refreshToken=refresh-token")));
    }
}
