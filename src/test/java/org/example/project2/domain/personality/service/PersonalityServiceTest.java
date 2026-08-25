package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.dto.FoodPreferencesUpdateRequest;
import org.example.project2.domain.personality.dto.PersonalityAnswerRequest;
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityAnswerRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.FoodCategory;
import org.example.project2.domain.user.entity.PersonalityOnboardingStatus;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalityServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPersonalityProfileRepository profileRepository;
    @Mock
    private UserPersonalityAnswerRepository answerRepository;

    private PersonalityService service;

    @BeforeEach
    void setUp() {
        service = new PersonalityService(
                userRepository,
                profileRepository,
                answerRepository,
                new PersonalityScoreCalculator(),
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
        verify(answerRepository).saveAll(anyList());
    }

    @Test
    void replacesAllFoodPreferences() {
        User user = user(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var response = service.updateFoodPreferences(
                user.getId(),
                new FoodPreferencesUpdateRequest(Set.of(FoodCategory.KOREAN, FoodCategory.JAPANESE))
        );

        assertThat(response.foodCategories())
                .containsExactlyInAnyOrder(FoodCategory.KOREAN, FoodCategory.JAPANESE);
        assertThat(user.getFoodPreferences())
                .containsExactlyInAnyOrder(FoodCategory.KOREAN, FoodCategory.JAPANESE);
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

        verify(profileRepository).delete(profile);
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
                Set.of(PersonalityTag.GOOD_LISTENER)
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
