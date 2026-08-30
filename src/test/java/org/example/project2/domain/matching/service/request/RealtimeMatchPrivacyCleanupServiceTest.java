package org.example.project2.domain.matching.service.request;

import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.service.proposal.MatchProposalLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMatchPrivacyCleanupServiceTest {
    @Mock MatchRequestRepository matchRequestRepository;
    @Mock MatchProposalRepository matchProposalRepository;
    @Mock MatchProposalLifecycleService matchProposalLifecycleService;
    @Mock RealtimeMatchRedisLifecycleService redisLifecycleService;

    private RealtimeMatchPrivacyCleanupService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeMatchPrivacyCleanupService(
                matchRequestRepository,
                matchProposalRepository,
                matchProposalLifecycleService,
                redisLifecycleService
        );
    }

    @Test
    void removesWaitingRequestAndItsRedisState() {
        UUID userId = UUID.randomUUID();
        MatchRequest request = org.mockito.Mockito.mock(MatchRequest.class);
        when(request.isWaiting()).thenReturn(true);
        when(matchRequestRepository.findAllByUserIdAndStatusIn(
                userId,
                List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING)
        )).thenReturn(List.of(request));

        service.removeActiveRequests(userId);

        verify(request).cancel();
        verify(redisLifecycleService).removeWaitingAfterCommit(request);
        verify(matchRequestRepository).delete(request);
    }

    @Test
    void cancelsPendingProposalBeforeRemovingConfirmingRequest() {
        UUID userId = UUID.randomUUID();
        MatchRequest request = org.mockito.Mockito.mock(MatchRequest.class);
        MatchProposal proposal = org.mockito.Mockito.mock(MatchProposal.class);
        when(request.getId()).thenReturn(2L);
        when(request.isConfirming()).thenReturn(true);
        when(proposal.getId()).thenReturn(20L);
        when(matchRequestRepository.findAllByUserIdAndStatusIn(
                userId,
                List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING)
        )).thenReturn(List.of(request));
        when(matchProposalRepository.findPendingByRequestId(2L)).thenReturn(Optional.of(proposal));
        when(matchProposalLifecycleService.cancelForRequest(20L, 2L)).thenReturn(proposal);

        service.removeActiveRequests(userId);

        verify(matchProposalLifecycleService).cancelForRequest(20L, 2L);
        verify(matchProposalRepository).delete(proposal);
        verify(matchRequestRepository).delete(request);
        verify(redisLifecycleService, never()).removeWaitingAfterCommit(request);
    }
}
