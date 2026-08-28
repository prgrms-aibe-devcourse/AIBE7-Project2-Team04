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
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{0, 1}, "PERSONALITY_FREE_TEXT_V2")
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
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                new PersonalityEmbeddingVector(
                        new float[]{1, 0},
                        "different-model",
                        "PERSONALITY_FREE_TEXT_V2"
                )
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void fallsBackToTagScoreWhenEmbeddingDimensionsDiffer() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{1, 0, 0}, "PERSONALITY_FREE_TEXT_V2")
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.tagScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void fallsBackToTagScoreWhenEmbeddingVersionFamiliesDiffer() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V3")
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.tagScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void ignoresLegacyEmbeddingVersionEvenWhenBothVectorsUseThatVersion() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                embedding(new float[]{1, 0}, "personality-document-v1:candidate-hash")
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.tagScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void fallsBackToTagScoreWhenOnlyOneEmbeddingIsPresent() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                null
        );

        assertThat(result.score()).isEqualTo((short) 100);
        assertThat(result.tagScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isNull();
    }

    @Test
    void keepsTagScoreAsTheFinalScoreWhenAiEmbeddingIsUnavailable() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER, PersonalityTag.FOOD_TALK),
                Set.of(PersonalityTag.GOOD_LISTENER),
                null,
                null
        );

        assertThat(result.available()).isTrue();
        assertThat(result.tagScore()).isEqualTo((short) 50);
        assertThat(result.embeddingScore()).isNull();
        assertThat(result.score()).isEqualTo((short) 50);
    }

    @Test
    void keepsVersionFamilySuffixesCompatibleWhenModelAndDimensionsMatch() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(),
                Set.of(),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2:request"),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2:candidate")
        );

        assertThat(result.available()).isTrue();
        assertThat(result.embeddingScore()).isEqualTo((short) 100);
        assertThat(result.score()).isEqualTo((short) 100);
    }

    @Test
    void doesNotIncludeTagSignalInTheEmbeddingScore() {
        PersonalityCompatibilityScore withMatchingTag = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.GOOD_LISTENER),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{0, 1}, "PERSONALITY_FREE_TEXT_V2")
        );
        PersonalityCompatibilityScore withDifferentTag = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(PersonalityTag.FOOD_TALK),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{0, 1}, "PERSONALITY_FREE_TEXT_V2")
        );

        assertThat(withMatchingTag.embeddingScore()).isEqualTo((short) 50);
        assertThat(withDifferentTag.embeddingScore()).isEqualTo((short) 50);
        assertThat(withMatchingTag.tagScore()).isEqualTo((short) 100);
        assertThat(withDifferentTag.tagScore()).isEqualTo((short) 0);
        assertThat(withMatchingTag.score()).isEqualTo((short) 90);
        assertThat(withDifferentTag.score()).isEqualTo((short) 10);
    }

    @Test
    void usesDesiredTextEmbeddingWhenCandidateHasNoProfileTags() {
        PersonalityCompatibilityScore result = calculator.calculate(
                Set.of(PersonalityTag.GOOD_LISTENER),
                Set.of(),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2"),
                embedding(new float[]{1, 0}, "PERSONALITY_FREE_TEXT_V2")
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

    @Test
    void validatesStoredEmbeddingForCurrentRankingBeforeUsingIt() {
        float[] validValues = new float[PersonalityEmbeddingVector.EXPECTED_DIMENSION];
        validValues[0] = 1;

        assertThat(new PersonalityEmbeddingVector(
                validValues,
                "embedding-model",
                "PERSONALITY_FREE_TEXT_V2:request"
        ).isValidForCurrentRanking()).isTrue();
        assertThat(new PersonalityEmbeddingVector(
                new float[]{1, 0},
                "embedding-model",
                "PERSONALITY_FREE_TEXT_V2"
        ).isValidForCurrentRanking()).isFalse();
        assertThat(new PersonalityEmbeddingVector(
                validValues,
                "embedding-model",
                "PERSONALITY_FREE_TEXT_V3"
        ).isValidForCurrentRanking()).isFalse();
    }

    private PersonalityEmbeddingVector embedding(float[] values, String sourceVersion) {
        return new PersonalityEmbeddingVector(values, "embedding-model", sourceVersion);
    }
}
