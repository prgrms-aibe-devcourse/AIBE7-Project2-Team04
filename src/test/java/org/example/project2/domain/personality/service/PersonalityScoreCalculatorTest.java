package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.service.calculation.PersonalityScoreCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityScoreCalculatorTest {
    private final PersonalityScoreCalculator calculator = new PersonalityScoreCalculator();

    @Test
    void convertsCardValuesToV1Scores() {
        assertThat(calculator.calculate(PersonalityAnswerValue.LOW)).isZero();
        assertThat(calculator.calculate(PersonalityAnswerValue.MEDIUM)).isEqualTo((short) 50);
        assertThat(calculator.calculate(PersonalityAnswerValue.HIGH)).isEqualTo((short) 100);
    }
}
