package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.exception.InvalidPersonalityInputException;
import org.springframework.stereotype.Component;

@Component
public class PersonalityScoreCalculator {

    public short calculate(PersonalityAnswerValue answerValue) {
        if (answerValue == null) {
            throw new InvalidPersonalityInputException("성향 응답값은 필수입니다.");
        }
        return (short) ((answerValue.getValue() - 1) * 100 / 4);
    }
}
