package org.example.project2.domain.matching.dto.proposal;

import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.personality.entity.PersonalityTag;

import java.time.Instant;
import java.util.List;

public record MatchProposalResponse(
        Long proposalId,
        Instant expiresAt,
        MatchProposalStatus status,
        MatchProposalDecision myDecision,
        MatchProposalPartnerProfileResponse partner,
        Short compatibilityScore,
        List<PersonalityTag> matchedTags,
        List<String> compatibilityReasons
) {
    public MatchProposalResponse(
            Long proposalId,
            Instant expiresAt,
            MatchProposalStatus status,
            MatchProposalDecision myDecision,
            MatchProposalPartnerProfileResponse partner,
            Short compatibilityScore,
            List<String> compatibilityReasons
    ) {
        this(
                proposalId,
                expiresAt,
                status,
                myDecision,
                partner,
                compatibilityScore,
                List.of(),
                compatibilityReasons
        );
    }

    public MatchProposalResponse {
        matchedTags = matchedTags == null
                ? List.of()
                : List.copyOf(matchedTags);
        compatibilityReasons = compatibilityReasons == null
                ? List.of()
                : List.copyOf(compatibilityReasons);
    }
}
