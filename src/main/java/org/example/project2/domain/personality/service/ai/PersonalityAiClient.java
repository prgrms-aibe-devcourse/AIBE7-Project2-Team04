package org.example.project2.domain.personality.service.ai;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Optional;
import java.util.Set;

public interface PersonalityAiClient {
    Optional<Set<PersonalityTag>> suggestTags(String selfDescription);

    Optional<java.util.List<String>> extractKeywords(String sourceText);

    Optional<float[]> embed(String sourceText);

    String embeddingModelName();
}
