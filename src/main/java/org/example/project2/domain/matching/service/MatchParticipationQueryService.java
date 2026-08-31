package org.example.project2.domain.matching.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.exception.InvalidMatchParticipantsException;
import org.example.project2.domain.matching.exception.MatchNotCompletedException;
import org.example.project2.domain.matching.exception.MatchNotFoundException;
import org.example.project2.domain.matching.exception.NotMatchParticipantException;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchParticipationQueryService {
    private final MatchRepository matchRepository;

    public MatchParticipation findCompletedParticipation(Long matchId, UUID userId) {
        if (matchId == null || userId == null) {
            throw new NotMatchParticipantException();
        }

        Match match = matchRepository.findByIdWithRequestsAndUsers(matchId)
                .orElseThrow(MatchNotFoundException::new);
        User request1User = match.getRequest1() == null ? null : match.getRequest1().getUser();
        User request2User = match.getRequest2() == null ? null : match.getRequest2().getUser();
        UUID request1UserId = userIdOf(request1User);
        UUID request2UserId = userIdOf(request2User);

        if (request1UserId == null || request2UserId == null
                || request1UserId.equals(request2UserId)) {
            throw new InvalidMatchParticipantsException();
        }
        if (!request1UserId.equals(userId) && !request2UserId.equals(userId)) {
            throw new NotMatchParticipantException();
        }
        if (!match.isCompleted()) {
            throw new MatchNotCompletedException();
        }
        if (match.getEndedAt() == null) {
            throw new InvalidMatchParticipantsException();
        }

        User reviewer = request1UserId.equals(userId) ? request1User : request2User;
        User reviewee = request1UserId.equals(userId) ? request2User : request1User;
        return new MatchParticipation(match, reviewer, reviewee);
    }

    private UUID userIdOf(User user) {
        return user == null ? null : user.getId();
    }

    public record MatchParticipation(
            Match match,
            User reviewer,
            User reviewee
    ) {
    }
}
