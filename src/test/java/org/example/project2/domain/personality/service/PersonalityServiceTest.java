package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.dto.FoodPreferencesUpdateRequest;
import org.example.project2.domain.personality.dto.PersonalityAnswerRequest;
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionRequest;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityAnswerRepository;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.calculation.PersonalityScoreCalculator;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingRequestedEvent;
import org.example.project2.domain.user.entity.FoodCategory;
import org.example.project2.domain.user.entity.PersonalityOnboardingStatus;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PersonalityServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPersonalityProfileRepository profileRepository;
    @Mock
    private UserPersonalityAnswerRepository answerRepository;
    @Mock
    private UserPersonalityEmbeddingRepository embeddingRepository;
    @Mock
    private PersonalityAiClient aiClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PersonalityService service;

    @BeforeEach
    void setUp() {
        service = new PersonalityService(
                userRepository,
                profileRepository,
                answerRepository,
                embeddingRepository,
                new PersonalityScoreCalculator(),
                aiClient,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void returnsIncompleteResponseWhenProfileDoesNotExist() {
        User user = user(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        PersonalityProfileResponse response = service.getProfile(user.getId());

        assertThat(response.completed()).isFalse();
        assertThat(response.onboardingStatus()).isEqualTo(PersonalityOnboardingStatus.NOT_STARTED);
        assertThat(response.questionnaireVersion()).isNull();
        assertThat(response.styleTags()).isEmpty();
    }

    @Test
    void returnsCompletedProfileWithoutRawAnswers() {
        User user = user(UUID.randomUUID());
        user.completePersonalityOnboarding();
        UserPersonalityProfile profile = UserPersonalityProfile.builder()
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 0)
                .mealPace((short) 50)
                .planningStyle((short) 100)
                .noveltyPreference((short) 50)
                .styleTags(new HashSet<>(Set.of(PersonalityTag.GOOD_LISTENER)))
                .completedAt(NOW)
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

        PersonalityProfileResponse response = service.getProfile(user.getId());

        assertThat(response.completed()).isTrue();
        assertThat(response.onboardingStatus()).isEqualTo(PersonalityOnboardingStatus.COMPLETED);
        assertThat(response.scores())
                .isEqualTo(new org.example.project2.domain.personality.dto.PersonalityScoresResponse(
                        (short) 0, (short) 50, (short) 100, (short) 50
                ));
        assertThat(response.styleTags()).containsExactly(PersonalityTag.GOOD_LISTENER);
    }

    @Test
    void savesProfileAnswersAndTagsInOneServiceCall() {
        User user = user(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any(UserPersonalityProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalityProfileResponse response = service.upsertProfile(user.getId(), request());

        assertThat(response.completed()).isTrue();
        assertThat(response.onboardingStatus()).isEqualTo(PersonalityOnboardingStatus.COMPLETED);
        assertThat(response.scores().conversationLevel()).isZero();
        assertThat(response.scores().mealPace()).isEqualTo((short) 50);
        assertThat(response.scores().planningStyle()).isEqualTo((short) 100);
        assertThat(response.styleTags()).containsExactly(PersonalityTag.GOOD_LISTENER);
        verify(answerRepository).deleteAllByUserId(user.getId());
        verify(answerRepository).saveAll(org.mockito.ArgumentMatchers.argThat(savedAnswers -> {
            Map<PersonalityDimension, Short> values = StreamSupport.stream(savedAnswers.spliterator(), false)
                    .collect(java.util.stream.Collectors.toMap(
                            org.example.project2.domain.personality.entity.UserPersonalityAnswer::getQuestionCode,
                            org.example.project2.domain.personality.entity.UserPersonalityAnswer::getAnswerValue
                    ));
            return values.equals(Map.of(
                    PersonalityDimension.CONVERSATION_LEVEL, (short) 1,
                    PersonalityDimension.MEAL_PACE, (short) 3,
                    PersonalityDimension.PLANNING_STYLE, (short) 5,
                    PersonalityDimension.NOVELTY_PREFERENCE, (short) 3
            ));
        }));
        verify(embeddingRepository).deleteById(user.getId());
    }

    @Test
    void storesConsentedDescriptionAndRequestsEmbeddingAfterStructuredSave() {
        User user = user(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any(UserPersonalityProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PersonalityProfileUpsertRequest request = new PersonalityProfileUpsertRequest(
                request().questionnaireVersion(), request().answers(), request().styleTags(),
                "  조용한 식사를 좋아해요.  ", true, List.of("한식", "대화")
        );

        PersonalityProfileResponse response = service.upsertProfile(user.getId(), request);

        assertThat(response.selfDescription()).isEqualTo("조용한 식사를 좋아해요.");
        assertThat(response.aiAnalysisConsent()).isTrue();
        var embeddingOrder = inOrder(embeddingRepository, eventPublisher);
        embeddingOrder.verify(embeddingRepository).deleteById(user.getId());
        embeddingOrder.verify(eventPublisher).publishEvent(new PersonalityEmbeddingRequestedEvent(
                user.getId(), "조용한 식사를 좋아해요."
        ));
    }

    @Test
    void consentWithdrawalDeletesDescriptionAndEmbeddingWithoutAiCall() {
        User user = user(UUID.randomUUID());
        UserPersonalityProfile profile = UserPersonalityProfile.builder()
                .user(user)
                .styleTags(new HashSet<>())
                .selfDescription("기존 설명")
                .aiAnalysisConsent(true)
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        PersonalityProfileResponse response = service.upsertProfile(user.getId(), request());

        assertThat(response.selfDescription()).isNull();
        assertThat(response.aiAnalysisConsent()).isFalse();
        verify(embeddingRepository).deleteById(user.getId());
        verifyNoInteractions(aiClient);
    }

    @Test
    void reportsUnavailableWhenAiTagSuggestionFails() {
        when(aiClient.suggestTags("소개")).thenReturn(Optional.empty());

        var response = service.suggestTags(new PersonalityTagSuggestionRequest("소개", true));

        assertThat(response.available()).isFalse();
        assertThat(response.suggestedTags()).isEmpty();
    }

    @Test
    void replacesExistingScoresAnswersAndTagsAsOneCompleteSet() {
        User user = user(UUID.randomUUID());
        UserPersonalityProfile profile = UserPersonalityProfile.builder()
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 0)
                .mealPace((short) 0)
                .planningStyle((short) 0)
                .noveltyPreference((short) 0)
                .styleTags(new HashSet<>(Set.of(PersonalityTag.GOOD_LISTENER)))
                .completedAt(NOW.minusSeconds(60))
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        PersonalityProfileUpsertRequest replacement = new PersonalityProfileUpsertRequest(
                PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1,
                List.of(
                        new PersonalityAnswerRequest(PersonalityDimension.CONVERSATION_LEVEL, PersonalityAnswerValue.HIGH),
                        new PersonalityAnswerRequest(PersonalityDimension.MEAL_PACE, PersonalityAnswerValue.HIGH),
                        new PersonalityAnswerRequest(PersonalityDimension.PLANNING_STYLE, PersonalityAnswerValue.HIGH),
                        new PersonalityAnswerRequest(PersonalityDimension.NOVELTY_PREFERENCE, PersonalityAnswerValue.HIGH)
                ),
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK, PersonalityTag.ENJOY_DESSERT),
                null,
                false,
                List.of("한식", "대화")
        );

        PersonalityProfileResponse response = service.upsertProfile(user.getId(), replacement);

        assertThat(response.scores().conversationLevel()).isEqualTo((short) 100);
        assertThat(response.scores().mealPace()).isEqualTo((short) 100);
        assertThat(response.scores().planningStyle()).isEqualTo((short) 100);
        assertThat(response.scores().noveltyPreference()).isEqualTo((short) 100);
        assertThat(response.styleTags()).containsExactly(PersonalityTag.FOOD_TALK);
        assertThat(profile.getStyleTags()).containsExactly(PersonalityTag.FOOD_TALK);
        verify(answerRepository).deleteAllByUserId(user.getId());
        verify(answerRepository).saveAll(anyList());
    }

    @Test
    void replacesAllFoodPreferences() {
        User user = user(UUID.randomUUID());
        user.replaceFoodPreferences(Set.of(FoodCategory.CHINESE, FoodCategory.WESTERN));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var response = service.updateFoodPreferences(
                user.getId(),
                new FoodPreferencesUpdateRequest(Set.of(FoodCategory.KOREAN))
        );

        assertThat(response.foodCategories())
                .containsExactly(FoodCategory.KOREAN);
        assertThat(user.getFoodPreferences())
                .containsExactly(FoodCategory.KOREAN);
    }

    @Test
    void storesSkippedStatusWhenProfileDoesNotExist() {
        User user = user(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        PersonalityProfileResponse response = service.skipProfile(user.getId());

        assertThat(response.completed()).isFalse();
        assertThat(response.onboardingStatus()).isEqualTo(PersonalityOnboardingStatus.SKIPPED);
        assertThat(user.getPersonalityOnboardingStatus()).isEqualTo(PersonalityOnboardingStatus.SKIPPED);
    }

    @Test
    void deletesProfileAndResetsOnboardingStatus() {
        User user = user(UUID.randomUUID());
        user.completePersonalityOnboarding();
        UserPersonalityProfile profile = UserPersonalityProfile.builder()
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 50)
                .mealPace((short) 50)
                .planningStyle((short) 50)
                .noveltyPreference((short) 50)
                .completedAt(NOW)
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findById(user.getId())).thenReturn(Optional.of(profile));

        service.resetProfile(user.getId());

        var deletionOrder = inOrder(answerRepository, embeddingRepository, profileRepository);
        deletionOrder.verify(answerRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(embeddingRepository).deleteById(user.getId());
        deletionOrder.verify(profileRepository).delete(profile);
        assertThat(user.getPersonalityOnboardingStatus()).isEqualTo(PersonalityOnboardingStatus.NOT_STARTED);
    }

    private PersonalityProfileUpsertRequest request() {
        return new PersonalityProfileUpsertRequest(
                PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1,
                List.of(
                        new PersonalityAnswerRequest(PersonalityDimension.CONVERSATION_LEVEL, PersonalityAnswerValue.LOW),
                        new PersonalityAnswerRequest(PersonalityDimension.MEAL_PACE, PersonalityAnswerValue.MEDIUM),
                        new PersonalityAnswerRequest(PersonalityDimension.PLANNING_STYLE, PersonalityAnswerValue.HIGH),
                        new PersonalityAnswerRequest(PersonalityDimension.NOVELTY_PREFERENCE, PersonalityAnswerValue.MEDIUM)
                ),
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK, PersonalityTag.ENJOY_DESSERT),
                null,
                false,
                List.of("한식", "대화")
        );
    }

    private User user(UUID id) {
        return User.builder()
                .id(id)
                .email("user@test.com")
                .passwordHash("encoded-password")
                .nickname("test-user")
                .build();
    }
}
