package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityCompatibilityCalculatorTest {

    private final PersonalityCompatibilityCalculator calculator = new PersonalityCompatibilityCalculator(
            new DesiredPersonalityTagScoreCalculator()
    );

    @Test
    void scoresRequestedTagsAgainstCandidateProfileTags() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.ENJOY_DESSERT),
                null,
                null
        );

        assertThat(result.available()).isTrue();
        assertThat(result.tagScore()).isEqualTo((short) 67);
        assertThat(result.score()).isEqualTo((short) 67);
        assertThat(result.embeddingScore()).isNull();
        assertThat(result.matchedTags())
                .containsExactlyInAnyOrder(PersonalityTag.GOOD_LISTENER, PersonalityTag.ENJOY_DESSERT);
    }

    @Test
    void limitsDesiredTextEmbeddingToTwentyPercentWhenTagScoreExists() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                embedding(new float[]{0, 1}, "personality-document-v1:candidate-hash")
        );

        assertThat(result.tagScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isEqualTo((short) 50);
        assertThat(result.score()).isEqualTo((short) 90);
    }

    @Test
    void fallsBackToTagScoreWhenEmbeddingMetadataIsIncompatible() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                new PersonalityEmbeddingVector(
                        new float[]{1, 0},
                        "different-model",
                        "personality-document-v1:candidate-hash"
                )
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void usesDesiredTextEmbeddingWhenCandidateHasNoProfileTags() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                embedding(new float[]{1, 0}, "personality-document-v1:candidate-hash")
        );

        assertThat(result.available()).isTrue();
        assertThat(result.tagScore()).isNull();
        assertThat(result.embeddingScore()).isEqualTo((short) 100);
        assertThat(result.score()).isEqualTo((short) 100);
    }

    @Test
    void isUnavailableWhenNeitherTagsNorCompatibleEmbeddingCanBeCompared() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(),
                Set.of(),
                null,
                null
        );

        assertThat(result).isEqualTo(PersonalityCompatibilityScore.unavailable(
                PersonalityCompatibilityCalculator.FORMULA_VERSION
        ));
    }

    private PersonalityEmbeddingVector embedding(float[] values, String sourceVersion) {
        return new PersonalityEmbeddingVector(values, "embedding-model", sourceVersion);
    }
}
