package org.example.project2.domain.personality.entity;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum PersonalityAnswerValue {
    LOW((short) 1),
    MEDIUM((short) 3),
    HIGH((short) 5);

    private final short value;

    PersonalityAnswerValue(short value) {
        this.value = value;
    }

    public static PersonalityAnswerValue from(short value) {
        return Arrays.stream(values())
                .filter(answerValue -> answerValue.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "성향 응답값은 1, 3, 5 중 하나여야 합니다."
                ));
    }
}
