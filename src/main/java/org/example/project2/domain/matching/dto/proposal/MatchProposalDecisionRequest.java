package org.example.project2.domain.matching.dto.proposal;

import jakarta.validation.constraints.NotNull;

public record MatchProposalDecisionRequest(
        @NotNull(message = "제안 결정은 ACCEPT 또는 REJECT여야 합니다.")
        MatchProposalDecisionType decision
) {
}
