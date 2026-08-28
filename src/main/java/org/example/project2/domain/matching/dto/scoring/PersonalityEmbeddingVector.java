package org.example.project2.domain.matching.dto.scoring;

import java.util.Objects;

public record PersonalityEmbeddingVector(
        float[] values,
        String modelName,
        String sourceVersion
) {
    public static final int EXPECTED_DIMENSION = 1536;
    public static final String CURRENT_SOURCE_VERSION_FAMILY = "PERSONALITY_FREE_TEXT_V2";

    public PersonalityEmbeddingVector {
        values = values == null ? null : values.clone();
        modelName = normalize(modelName);
        sourceVersion = normalize(sourceVersion);
    }

    @Override
    public float[] values() {
        return values == null ? null : values.clone();
    }

    public boolean hasMetadata() {
        return values != null && values.length > 0 && modelName != null && sourceVersion != null;
    }

    /**
     * 현재 실시간 매칭 산식에 넣을 수 있는 자유 서술 임베딩인지 검증한다.
     * 계산기 단위 테스트처럼 임의 차원을 사용하는 벡터와 저장 계층의 1536차원 벡터를 구분한다.
     */
    public boolean isValidForCurrentRanking() {
        if (!hasMetadata()
                || values.length != EXPECTED_DIMENSION
                || !CURRENT_SOURCE_VERSION_FAMILY.equals(versionFamily(sourceVersion))) {
            return false;
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 새 자유 텍스트 산식에서 사용할 수 있는 모델·차원·버전 계열인지 확인합니다.
     * 구버전 벡터는 양쪽이 같은 구버전이어도 호환 대상으로 취급하지 않습니다.
     */
    public boolean isCompatibleWith(PersonalityEmbeddingVector other) {
        return other != null
                && hasMetadata()
                && other.hasMetadata()
                && values.length == other.values.length
                && Objects.equals(modelName, other.modelName)
                && CURRENT_SOURCE_VERSION_FAMILY.equals(versionFamily(sourceVersion))
                && CURRENT_SOURCE_VERSION_FAMILY.equals(versionFamily(other.sourceVersion))
                && Objects.equals(versionFamily(sourceVersion), versionFamily(other.sourceVersion));
    }

    private static String versionFamily(String value) {
        if (value == null) {
            return null;
        }
        int hashSeparator = value.indexOf(':');
        return hashSeparator < 0 ? value : value.substring(0, hashSeparator);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
