package org.example.project2.domain.matching.service.result;

import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.exception.result.MatchResultException;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchResultQueryServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock MatchProposalRepository matchProposalRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock MatchResultResponseAssembler responseAssembler;
    @InjectMocks MatchResultQueryService service;

    @Test
    void returnsLatestResultFromAuthenticatedUsersView() {
        UUID userId = UUID.randomUUID();
        Match match = mock(Match.class);
        MatchRequest request1 = mock(MatchRequest.class);
        MatchRequest request2 = mock(MatchRequest.class);
        MatchProposal proposal = mock(MatchProposal.class);
        ChatRoom chatRoom = mock(ChatRoom.class);
        MatchResultResponse expected = mock(MatchResultResponse.class);
        MatchResultResponse other = mock(MatchResultResponse.class);
        when(match.getId()).thenReturn(10L);
        when(match.getRequest1()).thenReturn(request1);
        when(match.getRequest2()).thenReturn(request2);
        when(request1.getId()).thenReturn(1L);
        when(request2.getId()).thenReturn(2L);
        when(matchRepository.findLatestByParticipantUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(match));
        when(matchProposalRepository.findMatchedByRequestPair(1L, 2L)).thenReturn(Optional.of(proposal));
        when(chatRoomRepository.findByMatchId(10L)).thenReturn(Optional.of(chatRoom));
        when(responseAssembler.assemble(proposal, match, chatRoom)).thenReturn(
                new MatchResultResponseAssembler.MatchResultViews(userId, expected, UUID.randomUUID(), other)
        );

        assertThat(service.getLatest(userId)).isSameAs(expected);
    }

    @Test
    void returnsNotFoundWhenUserHasNoMatch() {
        UUID userId = UUID.randomUUID();
        when(matchRepository.findLatestByParticipantUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getLatest(userId))
                .isInstanceOf(MatchResultException.class);
    }
}
