package org.example.project2.domain.matching.service;

import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchParticipant;
import org.example.project2.domain.matching.entity.MatchParticipantRole;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchStatus;
import org.example.project2.domain.matching.exception.InvalidMatchParticipantsException;
import org.example.project2.domain.matching.exception.MatchNotCompletedException;
import org.example.project2.domain.matching.exception.NotMatchParticipantException;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchParticipationQueryServiceTest {
    @Mock MatchRepository matchRepository;
    @Mock MatchParticipantRepository matchParticipantRepository;

    private MatchParticipationQueryService service;

    @BeforeEach
    void setUp() {
        service = new MatchParticipationQueryService(matchRepository, matchParticipantRepository);
    }

    @Test
    void resolvesTheOtherParticipantOnlyForCompletedMatch() {
        UUID reviewerId = UUID.randomUUID();
        UUID revieweeId = UUID.randomUUID();
        Match match = completedMatch(reviewerId, revieweeId);
        when(matchRepository.findByIdWithRequestsAndUsers(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatchIdWithUser(301L))
                .thenReturn(participants(match, reviewerId, revieweeId));

        MatchParticipationQueryService.MatchParticipation result =
                service.findCompletedParticipation(301L, reviewerId);

        assertThat(result.match()).isSameAs(match);
        assertThat(result.reviewer().getId()).isEqualTo(reviewerId);
        assertThat(result.reviewee().getId()).isEqualTo(revieweeId);
    }

    @Test
    void rejectsAUserWhoIsNotOneOfTheTwoParticipants() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        Match match = completedMatch(firstUserId, secondUserId);
        when(matchRepository.findByIdWithRequestsAndUsers(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatchIdWithUser(301L))
                .thenReturn(participants(match, firstUserId, secondUserId));

        assertThatThrownBy(() -> service.findCompletedParticipation(301L, UUID.randomUUID()))
                .isInstanceOf(NotMatchParticipantException.class);
    }

    @Test
    void rejectsIncompleteMatchBeforeReviewServiceCanUseIt() {
        UUID reviewerId = UUID.randomUUID();
        UUID revieweeId = UUID.randomUUID();
        Match match = Match.builder()
                .request1(requestWithUser(reviewerId))
                .request2(requestWithUser(revieweeId))
                .matchedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .build();
        when(matchRepository.findByIdWithRequestsAndUsers(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatchIdWithUser(301L))
                .thenReturn(participants(match, reviewerId, revieweeId));

        assertThatThrownBy(() -> service.findCompletedParticipation(301L, reviewerId))
                .isInstanceOf(MatchNotCompletedException.class);
    }

    @Test
    void rejectsCancelledMatchEvenWhenItHasAnEndTimestamp() {
        UUID reviewerId = UUID.randomUUID();
        UUID revieweeId = UUID.randomUUID();
        Match match = Match.builder()
                .request1(requestWithUser(reviewerId))
                .request2(requestWithUser(revieweeId))
                .matchedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .status(MatchStatus.CANCELLED)
                .endedAt(Instant.parse("2026-08-02T12:00:00Z"))
                .build();
        when(matchRepository.findByIdWithRequestsAndUsers(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatchIdWithUser(301L))
                .thenReturn(participants(match, reviewerId, revieweeId));

        assertThatThrownBy(() -> service.findCompletedParticipation(301L, reviewerId))
                .isInstanceOf(MatchNotCompletedException.class);
    }

    @Test
    void rejectsMalformedParticipantCountInsteadOfGuessingTheTarget() {
        UUID reviewerId = UUID.randomUUID();
        UUID revieweeId = UUID.randomUUID();
        Match match = completedMatch(reviewerId, revieweeId);
        when(matchRepository.findByIdWithRequestsAndUsers(301L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findAllByMatchIdWithUser(301L)).thenReturn(List.of(
                MatchParticipant.builder()
                        .match(match)
                        .user(user(reviewerId))
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build()
        ));

        assertThatThrownBy(() -> service.findCompletedParticipation(301L, reviewerId))
                .isInstanceOf(InvalidMatchParticipantsException.class);
    }

    private Match completedMatch(UUID reviewerId, UUID revieweeId) {
        return Match.builder()
                .request1(requestWithUser(reviewerId))
                .request2(requestWithUser(revieweeId))
                .matchedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .status(org.example.project2.domain.matching.entity.MatchStatus.COMPLETED)
                .endedAt(Instant.parse("2026-08-02T12:00:00Z"))
                .build();
    }

    private MatchRequest requestWithUser(UUID userId) {
        return MatchRequest.builder().user(user(userId)).build();
    }

    private List<MatchParticipant> participants(Match match, UUID firstUserId, UUID secondUserId) {
        return List.of(
                MatchParticipant.builder()
                        .match(match)
                        .user(user(firstUserId))
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build(),
                MatchParticipant.builder()
                        .match(match)
                        .user(user(secondUserId))
                        .role(MatchParticipantRole.PARTICIPANT)
                        .build()
        );
    }

    private User user(UUID userId) {
        return User.builder()
                .id(userId)
                .email(userId + "@test.com")
                .nickname("사용자-" + userId.toString().substring(0, 8))
                .build();
    }
}
