package org.example.project2.domain.matching.dto.scoring;

public record WordSimilarityMatch(
        String sourceWord,
        String targetWord,
        short similarityPercent
) {
}
