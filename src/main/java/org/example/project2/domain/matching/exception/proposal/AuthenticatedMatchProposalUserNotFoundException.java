package org.example.project2.domain.matching.exception.proposal;

public class AuthenticatedMatchProposalUserNotFoundException extends RuntimeException {
    public AuthenticatedMatchProposalUserNotFoundException() {
        super("인증된 사용자를 찾을 수 없습니다.");
    }
}
