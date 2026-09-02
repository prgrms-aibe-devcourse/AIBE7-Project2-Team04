package org.example.project2.domain.matching.service.proposal;

import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.result.MatchResultDesiredLocationsResponse;
import org.example.project2.domain.matching.dto.result.MatchResultLocationResponse;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.entity.MatchStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResultWebSocketPublisherTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void sendsViewerSpecificResultToAuthenticatedUserDestination() {
        MatchResultWebSocketPublisher publisher = new MatchResultWebSocketPublisher(messagingTemplate);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchResultResponse request1Payload = response(request2UserId, "candidate");
        MatchResultResponse request2Payload = response(request1UserId, "source");

        publisher.publish(new MatchResultCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        ));

        verify(messagingTemplate).convertAndSendToUser(
                eq(request1UserId.toString()),
                eq(MatchResultWebSocketPublisher.DESTINATION),
                eq(request1Payload)
        );
        verify(messagingTemplate).convertAndSendToUser(
                eq(request2UserId.toString()),
                eq(MatchResultWebSocketPublisher.DESTINATION),
                eq(request2Payload)
        );
    }

    @Test
    void attemptsSecondDeliveryWhenFirstSessionFails() {
        MatchResultWebSocketPublisher publisher = new MatchResultWebSocketPublisher(messagingTemplate);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchResultResponse request1Payload = response(request2UserId, "candidate");
        MatchResultResponse request2Payload = response(request1UserId, "source");
        doThrow(new IllegalStateException("session closed"))
                .when(messagingTemplate)
                .convertAndSendToUser(
                        eq(request1UserId.toString()),
                        eq(MatchResultWebSocketPublisher.DESTINATION),
                        eq(request1Payload)
                );

        publisher.publish(new MatchResultCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        ));

        verify(messagingTemplate).convertAndSendToUser(
                eq(request2UserId.toString()),
                eq(MatchResultWebSocketPublisher.DESTINATION),
                eq(request2Payload)
        );
    }

    private MatchResultResponse response(UUID partnerId, String nickname) {
        return new MatchResultResponse(
                301L,
                MatchStatus.MATCHED,
                12L,
                null,
                new MatchProposalPartnerProfileResponse(
                        partnerId, nickname, null, null, java.util.Set.of()
                ),
                desiredLocations()
        );
    }

    private MatchResultDesiredLocationsResponse desiredLocations() {
        return new MatchResultDesiredLocationsResponse(
                new MatchResultLocationResponse("내 희망 장소", "서울특별시 강남구", "KOREAN", 37.501, 127.039),
                new MatchResultLocationResponse("상대 희망 장소", "서울특별시 강남구", "JAPANESE", 37.505, 127.045)
        );
    }
}
