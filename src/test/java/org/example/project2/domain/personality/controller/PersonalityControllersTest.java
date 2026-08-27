package org.example.project2.domain.personality.controller;

import org.example.project2.domain.personality.dto.FoodPreferencesResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityScoresResponse;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionResponse;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.exception.PersonalityExceptionHandler;
import org.example.project2.domain.personality.service.PersonalityService;
import org.example.project2.domain.user.entity.FoodCategory;
import org.example.project2.domain.user.entity.PersonalityOnboardingStatus;
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

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                PersonalityProfileController.class,
                FoodPreferenceController.class
        },
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
        PersonalityExceptionHandler.class
})
class PersonalityControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private PersonalityService personalityService;
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
    void getsOnlyAuthenticatedUsersProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.getProfile(userId)).thenReturn(
                PersonalityProfileResponse.incomplete(PersonalityOnboardingStatus.NOT_STARTED)
        );

        mockMvc.perform(get("/users/me/personality-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.completed").value(false));

        verify(personalityService).getProfile(userId);
    }

    @Test
    void profileResponseDoesNotExposeRawAnswersOrEmbeddingSourceText() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.getProfile(userId)).thenReturn(new PersonalityProfileResponse(
                PersonalityOnboardingStatus.COMPLETED,
                true,
                PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1,
                new PersonalityScoresResponse((short) 0, (short) 50, (short) 100, (short) 50),
                Set.of(PersonalityTag.GOOD_LISTENER),
                null,
                false
        ));

        mockMvc.perform(get("/users/me/personality-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scores.conversationLevel").value(0))
                .andExpect(jsonPath("$.data.answers").doesNotExist())
                .andExpect(jsonPath("$.data.sourceText").doesNotExist())
                .andExpect(jsonPath("$.data.embedding").doesNotExist());

        verify(personalityService).getProfile(userId);
    }

    @Test
    void doesNotProvideAnEndpointForAnotherUsersPersonalityProfile() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        mockMvc.perform(get("/users/{userId}/personality-profile", otherUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(requesterId)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(personalityService);
    }

    @Test
    void profileSubmissionRequiresCsrfToken() throws Exception {
        mockMvc.perform(put("/users/me/personality-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void rejectsUnsupportedAnswerValueWithPersonalityError() throws Exception {
        String invalidRequest = validProfileRequest().replace("\"value\": 1", "\"value\": 2");

        mockMvc.perform(put("/users/me/personality-profile")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void rejectsUnsupportedQuestionnaireVersionWithPersonalityError() throws Exception {
        String invalidRequest = validProfileRequest()
                .replace("MEAL_PERSONALITY_V1", "UNKNOWN_VERSION");

        mockMvc.perform(put("/users/me/personality-profile")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void rejectsUnsupportedPersonalityTagWithPersonalityError() throws Exception {
        String invalidRequest = validProfileRequest()
                .replace("GOOD_LISTENER", "UNKNOWN_TAG");

        mockMvc.perform(put("/users/me/personality-profile")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void rejectsMoreThanFivePersonalityTags() throws Exception {
        String invalidRequest = validProfileRequest().replace(
                "[\"GOOD_LISTENER\"]",
                "[\"GOOD_LISTENER\", \"FOOD_TALK\", \"LIGHT_CHAT\", \"DEEP_TALK\", \"COMFORTABLE_SILENCE\", \"CALM_ATMOSPHERE\"]"
        );

        mockMvc.perform(put("/users/me/personality-profile")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void rejectsUnsupportedFoodCategoryWithPersonalityError() throws Exception {
        mockMvc.perform(put("/users/me/food-preferences")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"foodCategories": ["UNKNOWN_FOOD"]}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void rejectsMoreThanFiveFoodCategories() throws Exception {
        mockMvc.perform(put("/users/me/food-preferences")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "foodCategories": [
                                    "KOREAN", "JAPANESE", "CHINESE",
                                    "WESTERN", "SOUTHEAST_ASIAN", "SNACK"
                                  ]
                                }
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("PERSONALITY_002"));
    }

    @Test
    void submitsValidProfileUsingJwtUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.upsertProfile(eq(userId), any())).thenReturn(
                PersonalityProfileResponse.incomplete(PersonalityOnboardingStatus.COMPLETED)
        );

        mockMvc.perform(put("/users/me/personality-profile")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileRequest()))
                .andExpect(status().isOk());

        verify(personalityService).upsertProfile(eq(userId), any());
    }

    @Test
    void tagSuggestionRequiresConsentAndCsrf() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/users/me/personality-profile/tag-suggestions")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfDescription":"조용한 식사를 좋아해요.","aiAnalysisConsent":false}
                                """))
                .andExpect(status().is(422));

        verifyNoInteractions(personalityService);
    }

    @Test
    void returnsSuggestionsWithoutSavingThem() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.suggestTags(any())).thenReturn(
                new PersonalityTagSuggestionResponse(true, Set.of(PersonalityTag.COMFORTABLE_SILENCE))
        );

        mockMvc.perform(post("/users/me/personality-profile/tag-suggestions")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfDescription":"조용한 식사를 좋아해요.","aiAnalysisConsent":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.suggestedTags[0]").value("COMFORTABLE_SILENCE"));

        verify(personalityService).suggestTags(any());
    }

    @Test
    void getsFoodPreferencesUsingJwtUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.getFoodPreferences(userId)).thenReturn(
                new FoodPreferencesResponse(Set.of(FoodCategory.KOREAN))
        );

        mockMvc.perform(get("/users/me/food-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.foodCategories[0]").value("KOREAN"));

        verify(personalityService).getFoodPreferences(userId);
    }

    @Test
    void updatesFoodPreferencesUsingJwtUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(personalityService.updateFoodPreferences(eq(userId), any())).thenReturn(
                new FoodPreferencesResponse(Set.of(FoodCategory.KOREAN, FoodCategory.JAPANESE))
        );

        mockMvc.perform(put("/users/me/food-preferences")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"foodCategories": ["KOREAN", "JAPANESE"]}
                                """))
                .andExpect(status().isOk());

        verify(personalityService).updateFoodPreferences(eq(userId), any());
    }

    private String bearerToken(UUID userId) {
        return "Bearer " + jwtProvider.issueToken(userId, UserRole.USER);
    }

    private String validProfileRequest() {
        return """
                {
                  "questionnaireVersion": "MEAL_PERSONALITY_V1",
                  "answers": [
                    {"questionCode": "CONVERSATION_LEVEL", "value": 1},
                    {"questionCode": "MEAL_PACE", "value": 3},
                    {"questionCode": "PLANNING_STYLE", "value": 5},
                    {"questionCode": "NOVELTY_PREFERENCE", "value": 3}
                  ],
                  "styleTags": ["GOOD_LISTENER"],
                  "selfDescription": null,
                  "aiAnalysisConsent": false
                }
                """;
    }

}
