package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.entity.MatchProposalDecision;
import org.example.project2.domain.matching.entity.MatchProposalStatus;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchProposalCreatedEventHandlerTest {
    @Mock
    private MatchProposalWebSocketPublisher webSocketPublisher;

    @Test
    void publishesNotificationWhenProposalTransactionCommits() {
        MatchProposalCreatedEventHandler handler = new MatchProposalCreatedEventHandler(webSocketPublisher);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchProposalResponse request1Payload = response(request2UserId);
        MatchProposalResponse request2Payload = response(request1UserId);
        MatchProposalCreatedEvent event = new MatchProposalCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        );

        handler.handle(event);

        verify(webSocketPublisher).publish(event);
    }

    private MatchProposalResponse response(UUID partnerId) {
        return new MatchProposalResponse(
                10L,
                Instant.parse("2099-01-01T00:00:15Z"),
                MatchProposalStatus.PENDING,
                MatchProposalDecision.PENDING,
                new MatchProposalPartnerProfileResponse(
                        partnerId,
                        "nickname",
                        null,
                        null,
                        Set.of(PersonalityTag.GOOD_LISTENER)
                ),
                (short) 74,
                List.of("tag match")
        );
    }
}
