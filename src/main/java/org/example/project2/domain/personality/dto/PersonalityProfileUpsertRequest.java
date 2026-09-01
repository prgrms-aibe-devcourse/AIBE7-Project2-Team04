package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.exception.InvalidPersonalityInputException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PersonalityProfileUpsertRequest(
        @NotNull(message = "설문 버전은 필수입니다.")
        @Schema(description = "설문 및 점수 계산 버전", example = "MEAL_PERSONALITY_V1")
        PersonalityQuestionnaireVersion questionnaireVersion,

        @NotNull(message = "네 가지 성향 응답은 필수입니다.")
        @Size(min = 4, max = 4, message = "네 가지 성향 차원에 모두 응답해야 합니다.")
        List<@Valid PersonalityAnswerRequest> answers,

        @NotNull(message = "성향 태그 목록은 필수입니다.")
        @Size(max = 5, message = "성향 태그는 최대 5개까지 선택할 수 있습니다.")
        @Schema(description = "세부 식사 스타일 태그, 최대 5개")
        Set<@NotNull(message = "성향 태그에는 null을 포함할 수 없습니다.") PersonalityTag> styleTags,

        @Size(max = 300, message = "자기소개는 최대 300자까지 입력할 수 있습니다.")
        @Schema(description = "AI 분석에 사용할 선택 입력. 동의하지 않으면 저장하지 않습니다.", maxLength = 300)
        String selfDescription,

        @Schema(description = "자기소개 AI 분석 동의 여부", example = "false")
        boolean aiAnalysisConsent,
        @Size(max = 5, message = "AI 키워드는 최대 5개까지 저장할 수 있습니다.")
        List<@NotNull(message = "AI 키워드에는 null을 포함할 수 없습니다.") String> aiKeywords
) {
    public PersonalityProfileUpsertRequest(
            PersonalityQuestionnaireVersion questionnaireVersion,
            List<PersonalityAnswerRequest> answers,
            Set<PersonalityTag> styleTags,
            String selfDescription,
            boolean aiAnalysisConsent
    ) {
        this(questionnaireVersion, answers, styleTags, selfDescription, aiAnalysisConsent, List.of());
    }

    public PersonalityProfileUpsertRequest {
        answers = answers == null ? null : List.copyOf(answers);
        styleTags = styleTags == null ? null : Set.copyOf(styleTags);
        selfDescription = normalizeDescription(selfDescription, aiAnalysisConsent);
        aiKeywords = normalizeKeywords(aiKeywords, aiAnalysisConsent);
    }

    public Map<PersonalityDimension, PersonalityAnswerValue> validatedAnswers() {
        if (questionnaireVersion != PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1) {
            throw new InvalidPersonalityInputException("지원하지 않는 설문 버전입니다.");
        }
        if (answers == null || answers.size() != PersonalityDimension.values().length) {
            throw new InvalidPersonalityInputException("네 가지 성향 차원에 모두 응답해야 합니다.");
        }
        if (styleTags == null || styleTags.size() > 5) {
            throw new InvalidPersonalityInputException("성향 태그는 최대 5개까지 선택할 수 있습니다.");
        }

        Map<PersonalityDimension, PersonalityAnswerValue> answerMap = new EnumMap<>(PersonalityDimension.class);
        for (PersonalityAnswerRequest answer : answers) {
            if (answer == null || answer.questionCode() == null || answer.value() == null) {
                throw new InvalidPersonalityInputException("성향 차원과 응답값은 필수입니다.");
            }
            if (answerMap.put(answer.questionCode(), answer.value()) != null) {
                throw new InvalidPersonalityInputException("같은 성향 차원을 중복 제출할 수 없습니다.");
            }
        }

        if (!answerMap.keySet().equals(EnumSet.allOf(PersonalityDimension.class))) {
            throw new InvalidPersonalityInputException("네 가지 성향 차원에 모두 응답해야 합니다.");
        }
        return Map.copyOf(answerMap);
    }

    private static String normalizeDescription(String description, boolean consent) {
        if (!consent || description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    private static List<String> normalizeKeywords(List<String> keywords, boolean consent) {
        if (!consent || keywords == null) return List.of();
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .distinct()
                .limit(5)
                .toList();
    }
}
