package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.DimensionMatchPreference;
import org.example.project2.domain.matching.dto.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.PersonalityEmbeddingVector;
import org.example.project2.domain.personality.dto.PersonalityScoresResponse;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.PreferenceMode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityCompatibilityCalculatorTest {

    private final PersonalityCompatibilityCalculator calculator = new PersonalityCompatibilityCalculator(
            new DesiredPersonalityTagScoreCalculator()
    );

    @Test
    void appliesSimilarComplementaryImportanceAndTagScore() {
        PersonalityCompatibilityScore result = calculator.calculate(
                scores(20, 20, 50, 50),
                scores(20, 80, 50, 50),
                Map.of(
                        PersonalityDimension.CONVERSATION_LEVEL,
                        new DimensionMatchPreference((short) 5, PreferenceMode.SIMILAR),
                        PersonalityDimension.MEAL_PACE,
                        new DimensionMatchPreference((short) 5, PreferenceMode.COMPLEMENTARY)
                ),
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
        assertThat(result.cardScore()).isEqualTo((short) 80);
        assertThat(result.tagScore()).isEqualTo((short) 67);
        assertThat(result.structuredScore()).isEqualTo((short) 77);
        assertThat(result.score()).isEqualTo((short) 77);
        assertThat(result.embeddingScore()).isNull();
        assertThat(result.matchedTags())
                .containsExactlyInAnyOrder(PersonalityTag.GOOD_LISTENER, PersonalityTag.ENJOY_DESSERT);
    }

    @Test
    void limitsEmbeddingToTwentyPercentOfFinalScore() {
        PersonalityCompatibilityScore result = calculator.calculate(
                scores(100, 50, 50, 50),
                scores(100, 50, 50, 50),
                Map.of(
                        PersonalityDimension.CONVERSATION_LEVEL,
                        new DimensionMatchPreference((short) 5, PreferenceMode.SIMILAR)
                ),
                Set.of(),
                Set.of(),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                embedding(new float[]{0, 1}, "personality-document-v1:candidate-hash")
        );

        assertThat(result.structuredScore()).isEqualTo((short) 100);
        assertThat(result.embeddingScore()).isEqualTo((short) 50);
        assertThat(result.score()).isEqualTo((short) 90);
    }

    @Test
    void fallsBackToStructuredScoreWhenEmbeddingMetadataIsIncompatible() {
        PersonalityCompatibilityScore result = calculator.calculate(
                scores(50, 50, 50, 50),
                scores(50, 50, 50, 50),
                Map.of(
                        PersonalityDimension.CONVERSATION_LEVEL,
                        new DimensionMatchPreference((short) 5, PreferenceMode.SIMILAR)
                ),
                Set.of(),
                Set.of(),
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
    void doesNotUseEmbeddingAsTheOnlyPersonalityScore() {
        PersonalityCompatibilityScore result = calculator.calculate(
                null,
                null,
                Map.of(),
                Set.of(),
                Set.of(),
                embedding(new float[]{1, 0}, "personality-document-v1:request-hash"),
                embedding(new float[]{1, 0}, "personality-document-v1:candidate-hash")
        );

        assertThat(result)
                .isEqualTo(PersonalityCompatibilityScore.unavailable(
                        PersonalityCompatibilityCalculator.FORMULA_VERSION
                ));
    }

    private PersonalityScoresResponse scores(int conversation, int mealPace, int planning, int novelty) {
        return new PersonalityScoresResponse(
                (short) conversation,
                (short) mealPace,
                (short) planning,
                (short) novelty
        );
    }

    private PersonalityEmbeddingVector embedding(float[] values, String sourceVersion) {
        return new PersonalityEmbeddingVector(values, "embedding-model", sourceVersion);
    }
}
