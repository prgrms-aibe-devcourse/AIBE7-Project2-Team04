package org.example.project2.domain.matching.controller.preference;

import org.example.project2.domain.matching.dto.preference.MatchingPreferenceItemResponse;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesResponse;
import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.matching.exception.preference.MatchingPreferenceExceptionHandler;
import org.example.project2.domain.matching.service.preference.MatchingPreferenceService;
import org.example.project2.domain.personality.entity.PersonalityDimension;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MatchingPreferenceController.class,
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
        MatchingPreferenceExceptionHandler.class
})
class MatchingPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MatchingPreferenceService matchingPreferenceService;
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
    void getsMatchingPreferencesUsingJwtUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(matchingPreferenceService.getPreferences(userId)).thenReturn(
                new MatchingPreferencesResponse(List.of(
                        new MatchingPreferenceItemResponse(
                                PersonalityDimension.CONVERSATION_LEVEL,
                                (short) 5,
                                PreferenceMode.SIMILAR
                        )
                ))
        );

        mockMvc.perform(get("/users/me/matching-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferences[0].dimension")
                        .value("CONVERSATION_LEVEL"))
                .andExpect(jsonPath("$.data.preferences[0].importance").value(5))
                .andExpect(jsonPath("$.data.preferences[0].mode").value("SIMILAR"));

        verify(matchingPreferenceService).getPreferences(userId);
    }

    @Test
    void matchingPreferenceLookupRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/users/me/matching-preferences"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchingPreferenceService);
    }

    @Test
    void replacesMatchingPreferencesUsingJwtUserIdAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        when(matchingPreferenceService.replacePreferences(eq(userId), any())).thenReturn(
                new MatchingPreferencesResponse(List.of())
        );

        mockMvc.perform(put("/users/me/matching-preferences")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMatchingPreferencesRequest()))
                .andExpect(status().isOk());

        verify(matchingPreferenceService).replacePreferences(eq(userId), any());
    }

    @Test
    void matchingPreferenceUpdateRequiresCsrfToken() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/users/me/matching-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMatchingPreferencesRequest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(matchingPreferenceService);
    }

    @Test
    void rejectsIncompleteMatchingPreferenceDimensions() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/users/me/matching-preferences")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferences": [
                                    {"dimension":"CONVERSATION_LEVEL","importance":5,"mode":"SIMILAR"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("MATCHING_001"));

        verifyNoInteractions(matchingPreferenceService);
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + jwtProvider.issueToken(userId, UserRole.USER);
    }

    private String validMatchingPreferencesRequest() {
        return """
                {
                  "preferences": [
                    {"dimension":"CONVERSATION_LEVEL","importance":5,"mode":"SIMILAR"},
                    {"dimension":"MEAL_PACE","importance":4,"mode":"SIMILAR"},
                    {"dimension":"PLANNING_STYLE","importance":2,"mode":"COMPLEMENTARY"},
                    {"dimension":"NOVELTY_PREFERENCE","importance":3,"mode":"SIMILAR"}
                  ]
                }
                """;
    }
}
