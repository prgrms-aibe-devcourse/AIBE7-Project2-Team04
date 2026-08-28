package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchProposalRetrySchedulerTest {

    @Mock MatchRequestRepository matchRequestRepository;
    @Mock MatchProposalSelectionAttemptService attemptService;
    @Mock MatchRequest request;
    @Mock User user;
    @InjectMocks MatchProposalRetryScheduler scheduler;

    @Test
    void retriesWaitingRequestAfterCursorWrapsToBeginning() {
        UUID userId = UUID.randomUUID();
        when(request.getId()).thenReturn(10L);
        when(request.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(matchRequestRepository.findAllByStatusAfterId(
                eq(MatchRequestStatus.WAITING), eq(0L), any(Pageable.class)
        )).thenReturn(List.of(request), List.of(request));
        when(matchRequestRepository.findAllByStatusAfterId(
                eq(MatchRequestStatus.WAITING), eq(10L), any(Pageable.class)
        )).thenReturn(List.of());
        when(attemptService.attempt(userId, 10L)).thenReturn(
                MatchProposalSelectionAttemptService.AttemptResult.RETRY_LATER,
                MatchProposalSelectionAttemptService.AttemptResult.CREATED
        );

        assertThat(scheduler.retryWaitingRequests()).isZero();
        assertThat(scheduler.retryWaitingRequests()).isEqualTo(1);

        verify(attemptService, org.mockito.Mockito.times(2)).attempt(userId, 10L);
    }

    @Test
    void keepsSchedulerAliveWhenWaitingRequestQueryTemporarilyFails() {
        when(matchRequestRepository.findAllByStatusAfterId(
                eq(MatchRequestStatus.WAITING), eq(0L), any(Pageable.class)
        )).thenThrow(new DataAccessResourceFailureException("temporary"));

        assertThat(scheduler.retryWaitingRequests()).isZero();

        verify(attemptService, never()).attempt(any(), any());
    }
}
