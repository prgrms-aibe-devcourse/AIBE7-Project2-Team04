package org.example.project2.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchParticipant;
import org.example.project2.domain.matching.entity.MatchParticipantRole;
import org.example.project2.domain.matching.exception.InvalidMatchParticipantsException;
import org.example.project2.domain.matching.exception.MatchNotCompletedException;
import org.example.project2.domain.matching.exception.MatchNotFoundException;
import org.example.project2.domain.matching.exception.NotMatchParticipantException;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 매칭 참여 관계를 다른 도메인이 안전하게 재사용할 수 있도록 제공하는 조회 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchParticipationQueryService {
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    /**
     * 인증 사용자의 완료 매칭 상대방을 결정합니다.
     *
     * <p>참여자 검증을 매칭 상태 검증보다 먼저 수행해, 비참여자에게 매칭의 진행 상태를 노출하지 않습니다.
     * 참여자 테이블과 요청 소유자 정보가 어긋난 경우에는 부분적인 추론으로 진행하지 않고
     * 내부 데이터 오류로 중단합니다.</p>
     */
    public MatchParticipation findCompletedParticipation(Long matchId, UUID userId) {
        if (matchId == null || userId == null) {
            throw new NotMatchParticipantException();
        }

        Match match = matchRepository.findByIdWithRequestsAndUsers(matchId)
                .orElseThrow(MatchNotFoundException::new);
        List<MatchParticipant> participants = matchParticipantRepository.findAllByMatchIdWithUser(matchId);
        if (participants == null) {
            throw new NotMatchParticipantException();
        }

        // 전체 참여자 구조를 검증하기 전에 호출자의 참여 여부만 확인합니다.
        // 비참여자가 추측한 matchId에 대해 내부 정합성 오류나 상대 정보를
        // 구분할 수 없도록 이후의 상세 검증은 실제 참여자에게만 수행합니다.
        boolean callerIsParticipant = participants.stream()
                .filter(participant -> participant != null && participant.getUser() != null)
                .map(MatchParticipant::getUser)
                .map(this::userIdOf)
                .anyMatch(userId::equals);
        if (!callerIsParticipant) {
            throw new NotMatchParticipantException();
        }
        if (participants.size() != 2) {
            throw new InvalidMatchParticipantsException();
        }

        User request1User = match.getRequest1() == null ? null : match.getRequest1().getUser();
        User request2User = match.getRequest2() == null ? null : match.getRequest2().getUser();
        UUID request1UserId = userIdOf(request1User);
        UUID request2UserId = userIdOf(request2User);
        if (request1UserId == null || request2UserId == null || request1UserId.equals(request2UserId)) {
            throw new InvalidMatchParticipantsException();
        }

        Map<UUID, User> usersById = new HashMap<>();
        for (MatchParticipant participant : participants) {
            if (participant == null
                    || participant.getRole() != MatchParticipantRole.PARTICIPANT
                    || participant.getUser() == null) {
                throw new InvalidMatchParticipantsException();
            }
            User participantUser = participant.getUser();
            UUID participantUserId = userIdOf(participantUser);
            if (participantUserId == null || usersById.putIfAbsent(participantUserId, participantUser) != null) {
                throw new InvalidMatchParticipantsException();
            }
        }

        if (!usersById.keySet().equals(Set.of(request1UserId, request2UserId))) {
            throw new InvalidMatchParticipantsException();
        }

        User reviewer = usersById.get(userId);
        if (reviewer == null) {
            throw new NotMatchParticipantException();
        }
        UUID revieweeId = request1UserId.equals(userId) ? request2UserId : request1UserId;
        User reviewee = usersById.get(revieweeId);
        if (reviewee == null || reviewee.getId() == null || reviewee.getId().equals(userId)) {
            throw new InvalidMatchParticipantsException();
        }

        if (!match.isCompleted()) {
            throw new MatchNotCompletedException();
        }
        if (match.getEndedAt() == null) {
            throw new InvalidMatchParticipantsException();
        }
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
