package org.example.project2.domain.personality.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PersonalityV1ContractTest {

    @Test
    void definesQuestionnaireVersionAndAnswerValues() {
        assertThat(PersonalityQuestionnaireVersion.values())
                .containsExactly(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1);
        assertThat(PersonalityAnswerValue.values())
                .extracting(PersonalityAnswerValue::getValue)
                .containsExactly((short) 1, (short) 3, (short) 5);
    }

    @Test
    void rejectsUnsupportedAnswerValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PersonalityAnswerValue.from((short) 2))
                .withMessage("성향 응답값은 1, 3, 5 중 하나여야 합니다.");
    }
}
