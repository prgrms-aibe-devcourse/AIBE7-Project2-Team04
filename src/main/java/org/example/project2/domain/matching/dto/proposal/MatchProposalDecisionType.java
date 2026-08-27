package org.example.project2.domain.matching.dto.proposal;

import org.example.project2.domain.matching.entity.MatchProposalDecision;

public enum MatchProposalDecisionType {
    ACCEPT,
    REJECT;

    public MatchProposalDecision toEntityDecision() {
        return this == ACCEPT
                ? MatchProposalDecision.ACCEPTED
                : MatchProposalDecision.REJECTED;
    }
}
