package org.example.project2.domain.personality.dto;

import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.exception.InvalidPersonalityInputException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalityProfileUpsertRequestTest {

    @Test
    void acceptsAllFourDimensionsOnce() {
        PersonalityProfileUpsertRequest request = request(List.of(
                answer(PersonalityDimension.CONVERSATION_LEVEL, PersonalityAnswerValue.LOW),
                answer(PersonalityDimension.MEAL_PACE, PersonalityAnswerValue.MEDIUM),
                answer(PersonalityDimension.PLANNING_STYLE, PersonalityAnswerValue.HIGH),
                answer(PersonalityDimension.NOVELTY_PREFERENCE, PersonalityAnswerValue.MEDIUM)
        ));

        assertThat(request.validatedAnswers())
                .containsOnlyKeys(PersonalityDimension.values());
    }

    @Test
    void rejectsDuplicatedAndMissingDimension() {
        PersonalityProfileUpsertRequest request = request(List.of(
                answer(PersonalityDimension.CONVERSATION_LEVEL, PersonalityAnswerValue.LOW),
                answer(PersonalityDimension.CONVERSATION_LEVEL, PersonalityAnswerValue.HIGH),
                answer(PersonalityDimension.PLANNING_STYLE, PersonalityAnswerValue.HIGH),
                answer(PersonalityDimension.NOVELTY_PREFERENCE, PersonalityAnswerValue.MEDIUM)
        ));

        assertThatThrownBy(request::validatedAnswers)
                .isInstanceOf(InvalidPersonalityInputException.class)
                .hasMessage("같은 성향 차원을 중복 제출할 수 없습니다.");
    }

    private PersonalityProfileUpsertRequest request(List<PersonalityAnswerRequest> answers) {
        return new PersonalityProfileUpsertRequest(
                PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1,
                answers,
                Set.of(PersonalityTag.GOOD_LISTENER)
        );
    }

    private PersonalityAnswerRequest answer(
            PersonalityDimension dimension,
            PersonalityAnswerValue value
    ) {
        return new PersonalityAnswerRequest(dimension, value);
    }
}
