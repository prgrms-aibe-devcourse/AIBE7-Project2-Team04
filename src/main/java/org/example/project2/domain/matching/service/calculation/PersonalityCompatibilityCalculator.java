package org.example.project2.domain.matching.service.calculation;

import org.example.project2.domain.matching.dto.scoring.DesiredPersonalityTagMatchScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityCompatibilityScore;
import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.example.project2.domain.personality.entity.PersonalityTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;
import java.util.Set;

@Slf4j
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
            log.info("[매칭 점수 산식 로그] 고정태그 점수={} (비중 80%), 임베딩 점수={} (비중 20%) => 최종 산출점수={}",
                    tagScore.score(), embeddingScore.getAsInt(), finalScore);
        } else if (tagScore.available()) {
            finalScore = tagScore.score();
            log.info("[매칭 점수 산식 로그] 고정태그 점수만 반영 (임베딩 없음) => 최종 산출점수={}", finalScore);
        } else {
            finalScore = (short) embeddingScore.getAsInt();
            log.info("[매칭 점수 산식 로그] 임베딩 점수만 반영 (태그 없음) => 최종 산출점수={}", finalScore);
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

    public PersonalityCompatibilityScore calculateWithList(
            Set<PersonalityTag> desiredTags,
            Set<PersonalityTag> candidateTags,
            PersonalityEmbeddingVector desiredFreeTextEmbedding,
            java.util.List<PersonalityEmbeddingVector> candidateEmbeddings
    ) {
        DesiredPersonalityTagMatchScore tagScore = tagScoreCalculator.calculate(desiredTags, candidateTags);
        OptionalInt embeddingScore = calculateEmbeddingScoreFromList(
                desiredFreeTextEmbedding,
                candidateEmbeddings
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
            log.info("[매칭 점수 산식 로그] (상대 희망태그 vs 후보태그={}) 고정태그점수={} (80%), (임베딩 유사도) 임베딩점수={} (20%) => 최종 산출점수={}",
                    tagScore.matchedTags(), tagScore.score(), embeddingScore.getAsInt(), finalScore);
        } else if (tagScore.available()) {
            finalScore = tagScore.score();
            log.info("[매칭 점수 산식 로그] (상대 희망태그 vs 후보태그={}) 고정태그점수만 반영={}", tagScore.matchedTags(), finalScore);
        } else {
            finalScore = (short) embeddingScore.getAsInt();
            log.info("[매칭 점수 산식 로그] 임베딩 유사도 점수만 반영={}", finalScore);
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

        double sim = dotProduct / (Math.sqrt(desiredNorm) * Math.sqrt(candidateNorm));
        sim = Math.max(-1.0, Math.min(1.0, sim));

        // 단어 세만틱 임베딩 공간 정밀 0~100점 스케일링 공식 (0~30점, 30~70점, 70~100점)
        double scaledScore;
        if (sim <= 0.20) {
            scaledScore = Math.max(0, (sim / 0.20) * 30.0);
        } else if (sim <= 0.50) {
            scaledScore = 30.0 + ((sim - 0.20) / 0.30) * 40.0;
        } else {
            scaledScore = 70.0 + Math.min(1.0, (sim - 0.50) / 0.50) * 30.0;
        }
        return OptionalInt.of((int) Math.round(scaledScore));
    }

    public OptionalInt calculateEmbeddingScoreFromList(
            PersonalityEmbeddingVector desiredFreeTextEmbedding,
            java.util.List<PersonalityEmbeddingVector> candidateEmbeddings
    ) {
        if (desiredFreeTextEmbedding == null || candidateEmbeddings == null || candidateEmbeddings.isEmpty()) {
            return OptionalInt.empty();
        }
        int maxScore = -1;
        for (PersonalityEmbeddingVector candidateEmb : candidateEmbeddings) {
            OptionalInt scoreOpt = calculateFreeTextEmbeddingScore(desiredFreeTextEmbedding, candidateEmb);
            if (scoreOpt.isPresent()) {
                maxScore = Math.max(maxScore, scoreOpt.getAsInt());
            }
        }
        return maxScore >= 0 ? OptionalInt.of(maxScore) : OptionalInt.empty();
    }

    public OptionalInt calculateFullMatrixCrossScore(
            java.util.List<PersonalityEmbeddingVector> desiredWordEmbeddings,
            java.util.List<PersonalityEmbeddingVector> candidateWordEmbeddings
    ) {
        if (desiredWordEmbeddings == null || desiredWordEmbeddings.isEmpty()
                || candidateWordEmbeddings == null || candidateWordEmbeddings.isEmpty()) {
            return OptionalInt.empty();
        }

        double totalBestScoreSum = 0;
        int validDesiredCount = 0;

        for (PersonalityEmbeddingVector desiredEmb : desiredWordEmbeddings) {
            int bestScoreForWord = -1;
            for (PersonalityEmbeddingVector candidateEmb : candidateWordEmbeddings) {
                OptionalInt scoreOpt = calculateFreeTextEmbeddingScore(desiredEmb, candidateEmb);
                if (scoreOpt.isPresent()) {
                    bestScoreForWord = Math.max(bestScoreForWord, scoreOpt.getAsInt());
                }
            }
            if (bestScoreForWord >= 0) {
                totalBestScoreSum += bestScoreForWord;
                validDesiredCount++;
            }
        }

        if (validDesiredCount == 0) {
            return OptionalInt.empty();
        }

        int finalScore = (int) Math.round(totalBestScoreSum / validDesiredCount);
        return OptionalInt.of(finalScore);
    }

    private short weightedAverage(int firstScore, int firstWeight, int secondScore, int secondWeight) {
        return (short) Math.round(
                (firstScore * firstWeight + secondScore * secondWeight)
                        / (double) (firstWeight + secondWeight)
        );
    }
}
