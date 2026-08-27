package org.example.project2.domain.matching.controller.request;

import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestResponse;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestStatusResponse;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestExceptionHandler;
import org.example.project2.domain.matching.service.request.RealtimeMatchRequestService;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.global.security.SecurityConfig;
import org.example.project2.global.security.auth.CustomUserDetailsService;
import org.example.project2.global.security.csrf.CsrfCookieFilter;
import org.example.project2.global.security.csrf.SpaCsrfTokenRequestHandler;
import org.example.project2.global.security.handler.RestAccessDeniedHandler;
import org.example.project2.global.security.handler.RestAuthenticationEntryPoint;
import org.example.project2.global.security.jwt.JwtFilter;
import org.example.project2.global.security.jwt.JwtProvider;
import org.example.project2.global.security.oauth.CustomOAuth2UserService;
import org.example.project2.global.security.oauth.OAuth2AuthenticationFailureHandler;
import org.example.project2.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RealtimeMatchRequestController.class,
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
        RestAccessDeniedHandler.class,
        RealtimeMatchRequestExceptionHandler.class
})
class RealtimeMatchRequestControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @MockitoBean RealtimeMatchRequestService service;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Test
    void createsRequestUsingJwtUserAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2099-01-01T00:05:00Z");
        when(service.create(eq(userId), any())).thenReturn(
                new RealtimeMatchRequestResponse(55L, MatchRequestStatus.WAITING, expiresAt)
        );

        mockMvc.perform(post("/matches/realtime/requests")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestId").value(55))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.expiresAt").value("2099-01-01T00:05:00Z"));

        verify(service).create(eq(userId), any());
    }

    @Test
    void creationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/matches/realtime/requests")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidDesiredTagsWithMatchingError() throws Exception {
        mockMvc.perform(post("/matches/realtime/requests")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "[\"GOOD_LISTENER\",\"FOOD_TALK\",\"ENJOY_DESSERT\"]",
                                "[\"GOOD_LISTENER\"]"
                        )))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("MATCHING_002"));

        verifyNoInteractions(service);
    }

    @Test
    void getsCurrentRequestUsingJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.getCurrent(userId)).thenReturn(new RealtimeMatchRequestStatusResponse(
                55L,
                MatchRequestStatus.WAITING,
                Instant.parse("2099-01-01T00:05:00Z")
        ));

        mockMvc.perform(get("/matches/realtime/requests/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(55));

        verify(service).getCurrent(userId);
    }

    @Test
    void cancelsOwnedRequestUsingJwtUserAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/matches/realtime/requests/55")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk());

        verify(service).cancel(userId, 55L);
    }

    @Test
    void requestEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/matches/realtime/requests/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + jwtProvider.issueToken(userId, UserRole.USER);
    }

    private String validRequest() {
        return """
                {
                  "foodCategory":"KOREAN",
                  "desiredTimeSlot":"2099-01-01T19:00:00Z",
                  "regionCode":"11680",
                  "regionName":"클라이언트 표시명",
                  "locationName":"강남역 11번 출구",
                  "latitude":37.501,
                  "longitude":127.039,
                  "searchRadius":3000,
                  "desiredPersonalityTags":["GOOD_LISTENER","FOOD_TALK","ENJOY_DESSERT"],
                  "desiredPersonalityText":"대화를 편하게 이어가는 분"
                }
                """;
    }
}
