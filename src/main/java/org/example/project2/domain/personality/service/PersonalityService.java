package org.example.project2.domain.personality.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.personality.dto.FoodPreferencesResponse;
import org.example.project2.domain.personality.dto.FoodPreferencesUpdateRequest;
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
import org.example.project2.domain.personality.dto.PersonalityScoresResponse;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionRequest;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionResponse;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityAnswer;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.exception.AuthenticatedUserNotFoundException;
import org.example.project2.domain.personality.exception.InvalidPersonalityInputException;
import org.example.project2.domain.personality.repository.UserPersonalityAnswerRepository;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.personality.service.calculation.PersonalityScoreCalculator;
import org.example.project2.domain.personality.service.embedding.PersonalityEmbeddingRequestedEvent;
import org.example.project2.domain.user.entity.FoodCategory;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalityService {
    private final UserRepository userRepository;
    private final UserPersonalityProfileRepository profileRepository;
    private final UserPersonalityAnswerRepository answerRepository;
    private final UserPersonalityEmbeddingRepository embeddingRepository;
    private final PersonalityScoreCalculator scoreCalculator;
    private final PersonalityAiClient aiClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PersonalityProfileResponse getProfile(UUID userId) {
        User user = findUser(userId);
        return profileRepository.findByUserId(userId)
                .map(profile -> toProfileResponse(user, profile))
                .orElseGet(() -> PersonalityProfileResponse.incomplete(
                        user.getPersonalityOnboardingStatus()
                ));
    }

    @Transactional
    public PersonalityProfileResponse upsertProfile(
            UUID userId,
            PersonalityProfileUpsertRequest request
    ) {
        Map<PersonalityDimension, PersonalityAnswerValue> answers = request.validatedAnswers();
        User user = findUser(userId);
        PersonalityScoresResponse scores = calculateScores(answers);
        Instant completedAt = clock.instant();

        UserPersonalityProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> UserPersonalityProfile.builder()
                        .user(user)
                        .build());

        profile.replace(
                request.questionnaireVersion(),
                scores.conversationLevel(),
                scores.mealPace(),
                scores.planningStyle(),
                scores.noveltyPreference(),
                request.styleTags(),
                request.selfDescription(),
                request.aiAnalysisConsent(),
                completedAt
        );
        UserPersonalityProfile savedProfile = profileRepository.save(profile);

        answerRepository.deleteAllByUserId(userId);
        List<UserPersonalityAnswer> answerEntities = answers.entrySet().stream()
                .map(entry -> UserPersonalityAnswer.builder()
                        .profile(savedProfile)
                        .questionCode(entry.getKey())
                        .answerValue(entry.getValue().getValue())
                        .build())
                .toList();
        answerRepository.saveAll(answerEntities);

        if (request.aiAnalysisConsent() && request.selfDescription() != null) {
            // 새 프로필이 커밋되기 전까지 이전 벡터가 매칭에 사용되지 않도록 제거합니다.
            embeddingRepository.deleteById(userId);
            eventPublisher.publishEvent(new PersonalityEmbeddingRequestedEvent(
                    userId,
                    savedProfile.getSelfDescription()
            ));
        } else {
            embeddingRepository.deleteById(userId);
        }

        user.completePersonalityOnboarding();
        return toProfileResponse(user, savedProfile);
    }

    public PersonalityTagSuggestionResponse suggestTags(PersonalityTagSuggestionRequest request) {
        return aiClient.suggestTags(request.selfDescription())
                .map(tags -> new PersonalityTagSuggestionResponse(true, tags))
                .orElseGet(() -> new PersonalityTagSuggestionResponse(false, Set.of()));
    }

    @Transactional
    public void resetProfile(UUID userId) {
        User user = findUser(userId);
        profileRepository.findById(userId).ifPresent(profile -> {
            answerRepository.deleteAllByUserId(userId);
            embeddingRepository.deleteById(userId);
            profileRepository.delete(profile);
        });
        user.resetPersonalityOnboarding();
    }

    @Transactional
    public PersonalityProfileResponse skipProfile(UUID userId) {
        User user = findUser(userId);
        return profileRepository.findByUserId(userId)
                .map(profile -> {
                    user.completePersonalityOnboarding();
                    return toProfileResponse(user, profile);
                })
                .orElseGet(() -> {
                    user.skipPersonalityOnboarding();
                    return PersonalityProfileResponse.incomplete(
                            user.getPersonalityOnboardingStatus()
                    );
                });
    }

    public FoodPreferencesResponse getFoodPreferences(UUID userId) {
        User user = findUser(userId);
        return new FoodPreferencesResponse(copyFoodCategories(user.getFoodPreferences()));
    }

    @Transactional
    public FoodPreferencesResponse updateFoodPreferences(
            UUID userId,
            FoodPreferencesUpdateRequest request
    ) {
        if (request.foodCategories() == null || request.foodCategories().size() > 5) {
            throw new InvalidPersonalityInputException(
                    "음식 카테고리는 최대 5개까지 선택할 수 있습니다."
            );
        }
        User user = findUser(userId);
        user.replaceFoodPreferences(request.foodCategories());
        return new FoodPreferencesResponse(copyFoodCategories(user.getFoodPreferences()));
    }

    private PersonalityScoresResponse calculateScores(
            Map<PersonalityDimension, PersonalityAnswerValue> answers
    ) {
        return new PersonalityScoresResponse(
                scoreCalculator.calculate(answers.get(PersonalityDimension.CONVERSATION_LEVEL)),
                scoreCalculator.calculate(answers.get(PersonalityDimension.MEAL_PACE)),
                scoreCalculator.calculate(answers.get(PersonalityDimension.PLANNING_STYLE)),
                scoreCalculator.calculate(answers.get(PersonalityDimension.NOVELTY_PREFERENCE))
        );
    }

    private PersonalityProfileResponse toProfileResponse(
            User user,
            UserPersonalityProfile profile
    ) {
        PersonalityScoresResponse scores = new PersonalityScoresResponse(
                profile.getConversationLevel(),
                profile.getMealPace(),
                profile.getPlanningStyle(),
                profile.getNoveltyPreference()
        );
        return new PersonalityProfileResponse(
                user.getPersonalityOnboardingStatus(),
                true,
                profile.getQuestionnaireVersion(),
                scores,
                copyTags(profile.getStyleTags()),
                profile.getSelfDescription(),
                profile.isAiAnalysisConsent()
        );
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private Set<PersonalityTag> copyTags(Set<PersonalityTag> tags) {
        return tags.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(tags));
    }

    private Set<FoodCategory> copyFoodCategories(Set<FoodCategory> categories) {
        return categories.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(categories));
    }
}
