package org.example.project2.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project2.domain.auth.dto.SignUpRequest;
import org.example.project2.domain.auth.dto.SignUpResponse;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeRequest;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeResponse;
import org.example.project2.domain.auth.service.local.AuthService;
import org.example.project2.domain.auth.service.oauth.OAuthTokenExchangeService;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.domain.user.entity.UserRole;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
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
                "app.auth.jwt.max-active-sessions=5",
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

    @Autowired
    private JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OAuthTokenExchangeService oauthTokenExchangeService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

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
        when(authCookieUtil.createAccessTokenCookie("access-token"))
                .thenReturn(ResponseCookie.from("accessToken", "access-token")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/")
                        .build());
        when(authCookieUtil.createRefreshTokenCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refreshToken", "refresh-token")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/")
                        .build());
        when(authCookieUtil.deleteLegacyRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/auth")
                        .maxAge(0)
                        .build());

        org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc.perform(post("/auth/oauth2/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.profileSetupRequired").value(true))
                .andReturn();

        java.util.List<String> setCookieHeaders = mvcResult.getResponse().getHeaders("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().anyMatch(h -> h.contains("accessToken=access-token")),
                "Set-Cookie 헤더에 accessToken 쿠키가 포함되어야 합니다."
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().anyMatch(h -> h.contains("refreshToken=refresh-token")),
                "Set-Cookie 헤더에 refreshToken 쿠키가 포함되어야 합니다."
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().anyMatch(h -> h.contains("refreshToken=")
                        && h.contains("Path=/auth") && h.contains("Max-Age=0")),
                "Set-Cookie 헤더로 레거시 /auth Refresh Token 쿠키를 삭제해야 합니다."
        );
    }

    @Test
    void oauthCodeExchangeHasOpenApiContract() throws Exception {
        var method = AuthController.class.getDeclaredMethod(
                "exchangeOAuthCode",
                OAuthTokenExchangeRequest.class,
                jakarta.servlet.http.HttpServletResponse.class
        );
        var operation = method.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
        var parameters = method.getAnnotation(io.swagger.v3.oas.annotations.Parameters.class);
        var responses = method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        var codeSchema = OAuthTokenExchangeRequest.class
                .getRecordComponents()[0]
                .getAccessor()
                .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);

        assertThat(operation.summary()).isEqualTo("OAuth 일회성 코드 교환");
        assertThat(parameters.value()).extracting(io.swagger.v3.oas.annotations.Parameter::name)
                .containsExactly("X-XSRF-TOKEN");
        assertThat(responses.value())
                .extracting(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode)
                .containsExactly("200", "400", "401");
        assertThat(codeSchema.description()).contains("한 번만 사용할 수 있는");
    }

    @Test
    void logoutRevokesRefreshTokenAndDeletesCookie() throws Exception {
        String accessToken = jwtProvider.issueToken(UUID.randomUUID(), UserRole.USER);
        when(authCookieUtil.deleteRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(0)
                        .build());
        when(authCookieUtil.deleteLegacyRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/auth")
                        .maxAge(0)
                        .build());
        when(authCookieUtil.deleteAccessTokenCookie())
                .thenReturn(ResponseCookie.from("accessToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(0)
                        .build());
        org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc.perform(post("/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(
                                new jakarta.servlet.http.Cookie("refreshToken", "refresh-token"),
                                new jakarta.servlet.http.Cookie("refreshToken", "legacy-refresh-token")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("accessToken="),
                                org.hamcrest.Matchers.containsString("Max-Age=0"))))
                .andReturn();

        verify(refreshTokenService).revoke("refresh-token");
        verify(refreshTokenService).revoke("legacy-refresh-token");
        java.util.List<String> setCookieHeaders = mvcResult.getResponse().getHeaders("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().anyMatch(h -> h.contains("refreshToken=")
                        && h.contains("Path=/") && !h.contains("Path=/auth") && h.contains("Max-Age=0")),
                "현재 / Refresh Token 쿠키를 삭제해야 합니다."
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().anyMatch(h -> h.contains("refreshToken=")
                        && h.contains("Path=/auth") && h.contains("Max-Age=0")),
                "레거시 /auth Refresh Token 쿠키를 삭제해야 합니다."
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                setCookieHeaders.stream().noneMatch(h -> h.startsWith("XSRF-TOKEN=")),
                "반복되는 상태 변경 요청을 위해 로그아웃 시 CSRF 쿠키를 삭제하지 않아야 합니다."
        );
    }
}
