package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchProposalSelectionAttemptServiceTest {

    @Mock MatchProposalSelectionService selectionService;
    @InjectMocks MatchProposalSelectionAttemptService attemptService;

    @Test
    void returnsCreatedOrNoCandidateFromSelectionResult() {
        UUID userId = UUID.randomUUID();
        MatchProposal proposal = mock(MatchProposal.class);
        when(selectionService.selectAndCreate(userId, 1L)).thenReturn(Optional.of(proposal));
        when(selectionService.selectAndCreate(userId, 2L)).thenReturn(Optional.empty());

        assertThat(attemptService.attempt(userId, 1L))
                .isEqualTo(MatchProposalSelectionAttemptService.AttemptResult.CREATED);
        assertThat(attemptService.attempt(userId, 2L))
                .isEqualTo(MatchProposalSelectionAttemptService.AttemptResult.NO_CANDIDATE);
    }

    @Test
    void skipsRequestThatAlreadyLeftWaitingState() {
        UUID userId = UUID.randomUUID();
        when(selectionService.selectAndCreate(userId, 1L)).thenThrow(
                new RealtimeMatchRequestException(RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT)
        );

        assertThat(attemptService.attempt(userId, 1L))
                .isEqualTo(MatchProposalSelectionAttemptService.AttemptResult.SKIPPED);
    }

    @Test
    void leavesTransientDatabaseFailureForNextRetryCycle() {
        UUID userId = UUID.randomUUID();
        when(selectionService.selectAndCreate(userId, 1L)).thenThrow(
                new DataAccessResourceFailureException("temporary")
        );

        assertThat(attemptService.attempt(userId, 1L))
                .isEqualTo(MatchProposalSelectionAttemptService.AttemptResult.RETRY_LATER);
    }
}
