package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.PersonalityOnboardingStatus;

import java.util.Set;

public record PersonalityProfileResponse(
        @Schema(example = "COMPLETED") PersonalityOnboardingStatus onboardingStatus,
        @Schema(example = "true") boolean completed,
        @Schema(nullable = true, example = "MEAL_PERSONALITY_V1") PersonalityQuestionnaireVersion questionnaireVersion,
        @Schema(nullable = true) PersonalityScoresResponse scores,
        Set<PersonalityTag> styleTags,
        @Schema(nullable = true, maxLength = 300) String selfDescription,
        boolean aiAnalysisConsent,
        java.util.List<String> aiKeywords
) {
    public PersonalityProfileResponse(
            PersonalityOnboardingStatus onboardingStatus,
            boolean completed,
            PersonalityQuestionnaireVersion questionnaireVersion,
            PersonalityScoresResponse scores,
            Set<PersonalityTag> styleTags,
            String selfDescription,
            boolean aiAnalysisConsent
    ) {
        this(onboardingStatus, completed, questionnaireVersion, scores, styleTags,
                selfDescription, aiAnalysisConsent, java.util.List.of());
    }

    public static PersonalityProfileResponse incomplete(PersonalityOnboardingStatus onboardingStatus) {
        return new PersonalityProfileResponse(onboardingStatus, false, null, null, Set.of(), null, false, java.util.List.of());
    }
}
