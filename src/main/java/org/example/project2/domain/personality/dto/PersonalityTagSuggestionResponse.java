package org.example.project2.domain.personality.dto;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Set;

public record PersonalityTagSuggestionResponse(
        boolean available,
        Set<PersonalityTag> suggestedTags
) {
    public PersonalityTagSuggestionResponse {
        suggestedTags = Set.copyOf(suggestedTags);
    }
}
