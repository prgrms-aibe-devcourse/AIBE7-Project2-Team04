package org.example.project2.domain.matching.dto.scoring;

import org.example.project2.domain.matching.service.calculation.DesiredPersonalityTagScoreCalculator;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DesiredPersonalityTagMatchScoreTest {

    private final DesiredPersonalityTagScoreCalculator calculator = new DesiredPersonalityTagScoreCalculator();

    @Test
    void calculatesScoreFromTheMatchedDesiredTags() {
        DesiredPersonalityTagMatchScore result = calculator.calculate(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.ENJOY_DESSERT)
        );

        assertThat(result.available()).isTrue();
        assertThat(result.score()).isEqualTo((short) 67);
        assertThat(result.matchedTags())
                .containsExactlyInAnyOrder(PersonalityTag.GOOD_LISTENER, PersonalityTag.ENJOY_DESSERT);
    }

    @Test
    void returnsUnavailableWhenEitherSideHasNoTags() {
        assertThat(calculator.calculate(Set.of(), Set.of(PersonalityTag.GOOD_LISTENER)))
                .isEqualTo(DesiredPersonalityTagMatchScore.unavailable());
        assertThat(calculator.calculate(Set.of(PersonalityTag.GOOD_LISTENER), Set.of()))
                .isEqualTo(DesiredPersonalityTagMatchScore.unavailable());
    }
}
