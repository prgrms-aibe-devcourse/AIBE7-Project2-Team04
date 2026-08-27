package org.example.project2.domain.matching.dto.proposal;

import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;

import java.time.Instant;
import java.util.List;

public record MatchProposalResponse(
        Long proposalId,
        Instant expiresAt,
        MatchProposalStatus status,
        MatchProposalDecision myDecision,
        MatchProposalPartnerProfileResponse partner,
        Short compatibilityScore,
        List<String> compatibilityReasons
) {
    public MatchProposalResponse {
        compatibilityReasons = compatibilityReasons == null
                ? List.of()
                : List.copyOf(compatibilityReasons);
    }
}
