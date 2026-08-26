package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.DesiredPersonalityTagMatchScore;
import org.example.project2.domain.matching.dto.DimensionMatchPreference;
import org.example.project2.domain.matching.dto.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.PersonalityEmbeddingVector;
import org.example.project2.domain.personality.dto.PersonalityScoresResponse;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.PreferenceMode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

@Component
public class PersonalityCompatibilityCalculator {
    public static final String FORMULA_VERSION = "PERSONALITY_MATCH_V1";

    private static final int CARD_WEIGHT_IN_STRUCTURED_SCORE = 80;
    private static final int TAG_WEIGHT_IN_STRUCTURED_SCORE = 20;
    private static final int STRUCTURED_WEIGHT_IN_FINAL_SCORE = 80;
    private static final int EMBEDDING_WEIGHT_IN_FINAL_SCORE = 20;

    private final DesiredPersonalityTagScoreCalculator tagScoreCalculator;

    public PersonalityCompatibilityCalculator(DesiredPersonalityTagScoreCalculator tagScoreCalculator) {
        this.tagScoreCalculator = tagScoreCalculator;
    }

    public PersonalityCompatibilityScore calculate(
            PersonalityScoresResponse requesterScores,
            PersonalityScoresResponse candidateScores,
            Map<PersonalityDimension, DimensionMatchPreference> preferences,
            Set<PersonalityTag> desiredTags,
            Set<PersonalityTag> candidateTags,
            PersonalityEmbeddingVector desiredDescriptionEmbedding,
            PersonalityEmbeddingVector candidateStyleEmbedding
    ) {
        OptionalInt cardScore = calculateCardScore(requesterScores, candidateScores, preferences);
        DesiredPersonalityTagMatchScore tagScore = tagScoreCalculator.calculate(desiredTags, candidateTags);
        OptionalInt structuredScore = combineStructuredScore(cardScore, tagScore);

        if (structuredScore.isEmpty()) {
            return PersonalityCompatibilityScore.unavailable(FORMULA_VERSION);
        }

        OptionalInt embeddingScore = calculateEmbeddingScore(
                desiredDescriptionEmbedding,
                candidateStyleEmbedding
        );
        short finalScore = embeddingScore.isPresent()
                ? weightedAverage(
                        structuredScore.getAsInt(), STRUCTURED_WEIGHT_IN_FINAL_SCORE,
                        embeddingScore.getAsInt(), EMBEDDING_WEIGHT_IN_FINAL_SCORE
                )
                : (short) structuredScore.getAsInt();

        return new PersonalityCompatibilityScore(
                true,
                finalScore,
                (short) structuredScore.getAsInt(),
                cardScore.isPresent() ? (short) cardScore.getAsInt() : null,
                tagScore.available() ? tagScore.score() : null,
                embeddingScore.isPresent() ? (short) embeddingScore.getAsInt() : null,
                tagScore.matchedTags(),
                FORMULA_VERSION
        );
    }

    private OptionalInt calculateCardScore(
            PersonalityScoresResponse requesterScores,
            PersonalityScoresResponse candidateScores,
            Map<PersonalityDimension, DimensionMatchPreference> preferences
    ) {
        if (requesterScores == null || candidateScores == null || preferences == null || preferences.isEmpty()) {
            return OptionalInt.empty();
        }

        long weightedScoreSum = 0;
        int importanceSum = 0;
        for (PersonalityDimension dimension : PersonalityDimension.values()) {
            DimensionMatchPreference preference = preferences.get(dimension);
            if (preference == null || preference.importance() == 0) {
                continue;
            }
            int requesterScore = scoreOf(requesterScores, dimension);
            int candidateScore = scoreOf(candidateScores, dimension);
            validateProfileScore(requesterScore);
            validateProfileScore(candidateScore);

            int difference = Math.abs(requesterScore - candidateScore);
            int dimensionScore = preference.mode() == PreferenceMode.SIMILAR
                    ? 100 - difference
                    : difference;
            weightedScoreSum += (long) dimensionScore * preference.importance();
            importanceSum += preference.importance();
        }

        if (importanceSum == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.round(weightedScoreSum / (double) importanceSum));
    }

    private OptionalInt combineStructuredScore(
            OptionalInt cardScore,
            DesiredPersonalityTagMatchScore tagScore
    ) {
        if (cardScore.isPresent() && tagScore.available()) {
            return OptionalInt.of(weightedAverage(
                    cardScore.getAsInt(), CARD_WEIGHT_IN_STRUCTURED_SCORE,
                    tagScore.score(), TAG_WEIGHT_IN_STRUCTURED_SCORE
            ));
        }
        if (cardScore.isPresent()) {
            return cardScore;
        }
        if (tagScore.available()) {
            return OptionalInt.of(tagScore.score());
        }
        return OptionalInt.empty();
    }

    private OptionalInt calculateEmbeddingScore(
            PersonalityEmbeddingVector desiredEmbedding,
            PersonalityEmbeddingVector candidateEmbedding
    ) {
        if (desiredEmbedding == null || !desiredEmbedding.isCompatibleWith(candidateEmbedding)) {
            return OptionalInt.empty();
        }

        float[] desiredValues = desiredEmbedding.values();
        float[] candidateValues = candidateEmbedding.values();
        double dotProduct = 0;
        double desiredNorm = 0;
        double candidateNorm = 0;
        for (int index = 0; index < desiredValues.length; index++) {
            float desiredValue = desiredValues[index];
            float candidateValue = candidateValues[index];
            if (!Float.isFinite(desiredValue) || !Float.isFinite(candidateValue)) {
                return OptionalInt.empty();
            }
            dotProduct += desiredValue * candidateValue;
            desiredNorm += desiredValue * desiredValue;
            candidateNorm += candidateValue * candidateValue;
        }
        if (desiredNorm == 0 || candidateNorm == 0) {
            return OptionalInt.empty();
        }

        double cosineSimilarity = dotProduct / (Math.sqrt(desiredNorm) * Math.sqrt(candidateNorm));
        double normalized = (Math.max(-1, Math.min(1, cosineSimilarity)) + 1) * 50;
        return OptionalInt.of((int) Math.round(normalized));
    }

    private int scoreOf(PersonalityScoresResponse scores, PersonalityDimension dimension) {
        return switch (dimension) {
            case CONVERSATION_LEVEL -> scores.conversationLevel();
            case MEAL_PACE -> scores.mealPace();
            case PLANNING_STYLE -> scores.planningStyle();
            case NOVELTY_PREFERENCE -> scores.noveltyPreference();
        };
    }

    private void validateProfileScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("성향 프로필 점수는 0 이상 100 이하여야 합니다.");
        }
    }

    private short weightedAverage(int firstScore, int firstWeight, int secondScore, int secondWeight) {
        return (short) Math.round(
                (firstScore * firstWeight + secondScore * secondWeight)
                        / (double) (firstWeight + secondWeight)
        );
    }
}
