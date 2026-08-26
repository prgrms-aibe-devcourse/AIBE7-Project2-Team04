package org.example.project2.domain.matching.dto;

import java.util.Objects;

public record PersonalityEmbeddingVector(
        float[] values,
        String modelName,
        String sourceVersion
) {
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

    public boolean isCompatibleWith(PersonalityEmbeddingVector other) {
        return other != null
                && hasMetadata()
                && other.hasMetadata()
                && values.length == other.values.length
                && Objects.equals(modelName, other.modelName)
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
