package org.example.project2.domain.review.controller;

import org.example.project2.domain.review.dto.MyReviewSummaryResponse;
import org.example.project2.domain.review.dto.PublicReviewSummaryResponse;
import org.example.project2.domain.review.dto.ReviewCreateResponse;
import org.example.project2.domain.review.dto.ReviewScoreStatus;
import org.example.project2.domain.review.entity.ImpressionTag;
import org.example.project2.domain.review.entity.RevisitIntention;
import org.example.project2.domain.review.exception.ReviewErrorCode;
import org.example.project2.domain.review.exception.ReviewException;
import org.example.project2.domain.review.exception.ReviewExceptionHandler;
import org.example.project2.domain.review.service.ReviewCommandService;
import org.example.project2.domain.review.service.ReviewQueryService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ReviewCommandController.class,
        ReviewQueryController.class
}, properties = {
        "app.auth.password.encoding-id=argon2",
        "app.auth.jwt.issuer=project2",
        "app.auth.jwt.audience=project2-api",
        "app.auth.jwt.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.auth.jwt.access-token-expiry=15m",
        "app.auth.jwt.refresh-token-expiry=14d",
        "app.auth.jwt.max-active-sessions=5",
        "app.auth.cors.allowed-origin=https://frontend.example"
})
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        JwtProvider.class,
        CsrfCookieFilter.class,
        SpaCsrfTokenRequestHandler.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ReviewExceptionHandler.class
})
class ReviewControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @MockitoBean ReviewCommandService reviewCommandService;
    @MockitoBean ReviewQueryService reviewQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Test
    void createsReviewUsingJwtUserAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reviewCommandService.create(eq(userId), any())).thenReturn(
                new ReviewCreateResponse(901L, Instant.parse("2026-08-31T12:00:00Z"))
        );

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewId").value(901))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-08-31T12:00:00Z"));

        verify(reviewCommandService).create(eq(userId), any());
    }

    @Test
    void creationRequiresCsrfToken() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/reviews")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reviewCommandService);
    }

    @Test
    void rejectsMissingRequiredIntentionWithKoreanValidationError() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":301,\"impressionTag\":\"PUNCTUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_002"))
                .andExpect(jsonPath("$.error.message").value("재만남 의향은 필수입니다."));

        verifyNoInteractions(reviewCommandService);
    }

    @Test
    void rejectsUnknownEnumCodeAsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":301,\"revisitIntention\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_002"));

        verifyNoInteractions(reviewCommandService);
    }

    @Test
    void rejectsUnknownTagCodeWithKoreanBadRequestMessage() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":301,\"revisitIntention\":\"DEFINITELY_AGAIN\",\"impressionTag\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_002"))
                .andExpect(jsonPath("$.error.message")
                        .value("지원하지 않는 후기 코드 또는 잘못된 JSON이 포함되어 있습니다."));

        verifyNoInteractions(reviewCommandService);
    }

    @Test
    void rejectsMalformedJsonWithKoreanBadRequestMessage() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":301,\"revisitIntention\":\"DEFINITELY_AGAIN\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_002"))
                .andExpect(jsonPath("$.error.message")
                        .value("지원하지 않는 후기 코드 또는 잘못된 JSON이 포함되어 있습니다."));

        verifyNoInteractions(reviewCommandService);
    }

    @Test
    void mapsReviewBusinessErrorWithoutLeakingInternalDetails() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reviewCommandService.create(eq(userId), any()))
                .thenThrow(new ReviewException(ReviewErrorCode.REVIEW_PERIOD_EXPIRED));

        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("REVIEW_PERIOD_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("후기 작성 기간이 지났습니다."));
    }

    @Test
    void returnsMyReviewSummaryUsingJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reviewQueryService.getMyReviewSummary(userId)).thenReturn(
                new MyReviewSummaryResponse(ReviewScoreStatus.AVAILABLE, new BigDecimal("84.0"), 8)
        );

        mockMvc.perform(get("/mypage/reviews")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.dasiHankkiScore").value(84.0))
                .andExpect(jsonPath("$.data.validReviewCount").value(8));

        verify(reviewQueryService).getMyReviewSummary(userId);
    }

    @Test
    void returnsPublicReviewSummaryWithoutExposingRawReviewFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reviewQueryService.getPublicReviewSummary(userId)).thenReturn(
                new PublicReviewSummaryResponse(ReviewScoreStatus.INSUFFICIENT_REVIEWS, null, 2)
        );

        mockMvc.perform(get("/users/{userId}/reviews", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreStatus").value("INSUFFICIENT_REVIEWS"))
                .andExpect(jsonPath("$.data.dasiHankkiScore").doesNotExist())
                .andExpect(jsonPath("$.data.validReviewCount").value(2))
                .andExpect(jsonPath("$.data.revisitIntention").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist());

        verify(reviewQueryService).getPublicReviewSummary(userId);
    }

    @Test
    void reviewEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/mypage/reviews"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/{userId}/reviews", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewCommandService, reviewQueryService);
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + jwtProvider.issueToken(userId, UserRole.USER);
    }

    private String validRequest() {
        return """
                {
                  "matchId": 301,
                  "revisitIntention": "%s",
                  "impressionTag": "%s"
                }
                """.formatted(RevisitIntention.DEFINITELY_AGAIN, ImpressionTag.PUNCTUAL);
    }
}
