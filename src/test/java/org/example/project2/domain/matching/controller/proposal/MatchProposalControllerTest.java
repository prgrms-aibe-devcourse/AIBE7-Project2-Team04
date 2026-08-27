package org.example.project2.domain.matching.controller.proposal;

import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionRequest;
import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionType;
import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.matching.exception.proposal.MatchProposalExceptionHandler;
import org.example.project2.domain.matching.service.proposal.MatchProposalInteractionService;
import org.example.project2.domain.personality.entity.PersonalityTag;
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
import java.util.List;
import java.util.Set;
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

@WebMvcTest(
        controllers = MatchProposalController.class,
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
        MatchProposalExceptionHandler.class
})
class MatchProposalControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @MockitoBean MatchProposalInteractionService service;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    @MockitoBean OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Test
    void getsCurrentProposalWithBriefPartnerProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        MatchProposalResponse response = response();
        when(service.getCurrent(userId)).thenReturn(response);

        mockMvc.perform(get("/matches/realtime/proposals/current")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalId").value(10))
                .andExpect(jsonPath("$.data.partner.nickname").value("상대 닉네임"))
                .andExpect(jsonPath("$.data.partner.profileImageUrl")
                        .value("https://cdn.example/profile.png"))
                .andExpect(jsonPath("$.data.partner.description")
                        .value("같이 편하게 식사하고 싶어요."))
                .andExpect(jsonPath("$.data.partner.styleTags[0]").value("GOOD_LISTENER"))
                .andExpect(jsonPath("$.data.compatibilityScore").value(74));

        verify(service).getCurrent(userId);
    }

    @Test
    void acceptsProposalWithCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.decide(eq(userId), eq(10L), any(MatchProposalDecisionRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/matches/realtime/proposals/10/decision")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalId").value(10));

        verify(service).decide(eq(userId), eq(10L),
                eq(new MatchProposalDecisionRequest(MatchProposalDecisionType.ACCEPT)));
    }

    @Test
    void decisionRequiresCsrf() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/matches/realtime/proposals/10/decision")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void proposalEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/matches/realtime/proposals/current"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private MatchProposalResponse response() {
        return new MatchProposalResponse(
                10L,
                Instant.parse("2099-01-01T00:00:15Z"),
                MatchProposalStatus.PENDING,
                MatchProposalDecision.PENDING,
                new MatchProposalPartnerProfileResponse(
                        UUID.randomUUID(),
                        "상대 닉네임",
                        "https://cdn.example/profile.png",
                        "같이 편하게 식사하고 싶어요.",
                        Set.of(PersonalityTag.GOOD_LISTENER)
                ),
                (short) 74,
                List.of("대화 선호가 잘 맞아요.")
        );
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + jwtProvider.issueToken(userId, UserRole.USER);
    }
}
