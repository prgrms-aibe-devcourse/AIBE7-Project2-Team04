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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchProposalWebSocketPublisherTest {
    private static final Instant EXPIRES_AT = Instant.parse("2099-01-01T00:00:15Z");

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void sendsEachViewerSpecificPayloadToAuthenticatedUserDestination() {
        MatchProposalWebSocketPublisher publisher = new MatchProposalWebSocketPublisher(messagingTemplate);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchProposalResponse request1Payload = response(10L, request2UserId, "candidate");
        MatchProposalResponse request2Payload = response(10L, request1UserId, "source");

        publisher.publish(new MatchProposalCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        ));

        verify(messagingTemplate).convertAndSendToUser(
                eq(request1UserId.toString()),
                eq(MatchProposalWebSocketPublisher.DESTINATION),
                eq(request1Payload)
        );
        verify(messagingTemplate).convertAndSendToUser(
                eq(request2UserId.toString()),
                eq(MatchProposalWebSocketPublisher.DESTINATION),
                eq(request2Payload)
        );
        assertThat(request1Payload.expiresAt()).isEqualTo(request2Payload.expiresAt());
    }

    @Test
    void attemptsSecondDeliveryWhenFirstSessionFails() {
        MatchProposalWebSocketPublisher publisher = new MatchProposalWebSocketPublisher(messagingTemplate);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchProposalResponse request1Payload = response(10L, request2UserId, "candidate");
        MatchProposalResponse request2Payload = response(10L, request1UserId, "source");
        doThrow(new IllegalStateException("session closed"))
                .when(messagingTemplate)
                .convertAndSendToUser(
                        eq(request1UserId.toString()),
                        eq(MatchProposalWebSocketPublisher.DESTINATION),
                        eq(request1Payload)
                );

        publisher.publish(new MatchProposalCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        ));

        verify(messagingTemplate).convertAndSendToUser(
                eq(request2UserId.toString()),
                eq(MatchProposalWebSocketPublisher.DESTINATION),
                eq(request2Payload)
        );
    }

    private MatchProposalResponse response(Long proposalId, UUID partnerId, String nickname) {
        return new MatchProposalResponse(
                proposalId,
                EXPIRES_AT,
                MatchProposalStatus.PENDING,
                MatchProposalDecision.PENDING,
                new MatchProposalPartnerProfileResponse(
                        partnerId,
                        nickname,
                        null,
                        null,
                        Set.of(PersonalityTag.GOOD_LISTENER)
                ),
                (short) 74,
                List.of("tag match")
        );
    }
}
