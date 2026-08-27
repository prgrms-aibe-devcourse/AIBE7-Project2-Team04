package org.example.project2.domain.matching.dto.scoring;

import java.util.List;

public record BidirectionalMatchScoreSnapshot(
        Short sourceToTargetScore,
        List<String> sourceToTargetReasons,
        Short targetToSourceScore,
        List<String> targetToSourceReasons,
        Short pairScore,
        String formulaVersion
) {
    public BidirectionalMatchScoreSnapshot {
        validateNullableScore(sourceToTargetScore, "정방향 성향 호환도");
        validateNullableScore(targetToSourceScore, "역방향 성향 호환도");
        validateNullableScore(pairScore, "최종 후보 쌍 호환도");
        sourceToTargetReasons = copyReasons(sourceToTargetReasons);
        targetToSourceReasons = copyReasons(targetToSourceReasons);

        boolean hasScoreData = sourceToTargetScore != null
                || targetToSourceScore != null
                || pairScore != null
                || !sourceToTargetReasons.isEmpty()
                || !targetToSourceReasons.isEmpty();
        if (hasScoreData && (formulaVersion == null || formulaVersion.isBlank())) {
            throw new IllegalArgumentException("성향 점수 스냅샷의 산식 버전은 필수입니다.");
        }
        formulaVersion = formulaVersion == null ? null : formulaVersion.trim();
    }

    public BidirectionalMatchScoreSnapshot reversed() {
        return new BidirectionalMatchScoreSnapshot(
                targetToSourceScore,
                targetToSourceReasons,
                sourceToTargetScore,
                sourceToTargetReasons,
                pairScore,
                formulaVersion
        );
    }

    private static List<String> copyReasons(List<String> reasons) {
        if (reasons == null) {
            return List.of();
        }
        if (reasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("성향 호환 사유는 비어 있을 수 없습니다.");
        }
        return List.copyOf(reasons);
    }

    private static void validateNullableScore(Short score, String name) {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException(name + " 점수는 0 이상 100 이하여야 합니다.");
        }
    }
}
