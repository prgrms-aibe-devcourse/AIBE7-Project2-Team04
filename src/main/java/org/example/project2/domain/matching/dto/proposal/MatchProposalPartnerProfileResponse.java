package org.example.project2.domain.matching.dto.proposal;

import org.example.project2.domain.personality.entity.PersonalityTag;

import java.util.Set;
import java.util.UUID;

public record MatchProposalPartnerProfileResponse(
        UUID userId,
        String nickname,
        String profileImageUrl,
        String description,
        Set<PersonalityTag> styleTags
) {
    public MatchProposalPartnerProfileResponse {
        styleTags = styleTags == null ? Set.of() : Set.copyOf(styleTags);
    }
}
