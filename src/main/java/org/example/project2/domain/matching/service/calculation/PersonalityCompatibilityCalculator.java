package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.scoring.DesiredPersonalityTagMatchScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;
import java.util.Set;

@Component
public class PersonalityCompatibilityCalculator {
    public static final String FORMULA_VERSION = "DESIRED_PERSONALITY_MATCH_V1";

    private static final int TAG_WEIGHT_IN_FINAL_SCORE = 80;
    private static final int EMBEDDING_WEIGHT_IN_FINAL_SCORE = 20;

    private final DesiredPersonalityTagScoreCalculator tagScoreCalculator;

    public PersonalityCompatibilityCalculator(DesiredPersonalityTagScoreCalculator tagScoreCalculator) {
        this.tagScoreCalculator = tagScoreCalculator;
    }

    /**
     * 요청자의 희망 태그와 후보자의 확정 태그, 그리고 양쪽 자유 텍스트 벡터를 독립적으로 계산합니다.
     * 카드 응답·차원 점수는 이 산식의 입력으로 받지 않습니다.
     */
    public PersonalityCompatibilityScore calculate(
            Set<PersonalityTag> desiredTags,
            Set<PersonalityTag> candidateTags,
            PersonalityEmbeddingVector desiredFreeTextEmbedding,
            PersonalityEmbeddingVector candidateSelfDescriptionEmbedding
    ) {
        DesiredPersonalityTagMatchScore tagScore = tagScoreCalculator.calculate(desiredTags, candidateTags);
        OptionalInt embeddingScore = calculateFreeTextEmbeddingScore(
                desiredFreeTextEmbedding,
                candidateSelfDescriptionEmbedding
        );
        if (!tagScore.available() && embeddingScore.isEmpty()) {
            return PersonalityCompatibilityScore.unavailable(FORMULA_VERSION);
        }

        short finalScore;
        if (tagScore.available() && embeddingScore.isPresent()) {
            finalScore = weightedAverage(
                    tagScore.score(), TAG_WEIGHT_IN_FINAL_SCORE,
                    embeddingScore.getAsInt(), EMBEDDING_WEIGHT_IN_FINAL_SCORE
            );
        } else if (tagScore.available()) {
            finalScore = tagScore.score();
        } else {
            finalScore = (short) embeddingScore.getAsInt();
        }

        return new PersonalityCompatibilityScore(
                true,
                finalScore,
                tagScore.available() ? tagScore.score() : null,
                embeddingScore.isPresent() ? (short) embeddingScore.getAsInt() : null,
                tagScore.matchedTags(),
                FORMULA_VERSION
        );
    }

    private OptionalInt calculateFreeTextEmbeddingScore(
            PersonalityEmbeddingVector desiredFreeTextEmbedding,
            PersonalityEmbeddingVector candidateSelfDescriptionEmbedding
    ) {
        if (desiredFreeTextEmbedding == null
                || !desiredFreeTextEmbedding.isCompatibleWith(candidateSelfDescriptionEmbedding)) {
            return OptionalInt.empty();
        }

        float[] desiredValues = desiredFreeTextEmbedding.values();
        float[] candidateValues = candidateSelfDescriptionEmbedding.values();
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

    private short weightedAverage(int firstScore, int firstWeight, int secondScore, int secondWeight) {
        return (short) Math.round(
                (firstScore * firstWeight + secondScore * secondWeight)
                        / (double) (firstWeight + secondWeight)
        );
    }
}
