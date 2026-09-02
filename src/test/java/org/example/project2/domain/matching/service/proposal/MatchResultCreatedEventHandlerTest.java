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

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResultCreatedEventHandlerTest {
    @Mock
    private MatchResultWebSocketPublisher webSocketPublisher;

    @Test
    void publishesOnlyThroughResultPublisherAfterCommitCallback() {
        MatchResultCreatedEventHandler handler = new MatchResultCreatedEventHandler(webSocketPublisher);
        UUID request1UserId = UUID.randomUUID();
        UUID request2UserId = UUID.randomUUID();
        MatchResultResponse request1Payload = response(request2UserId);
        MatchResultResponse request2Payload = response(request1UserId);
        MatchResultCreatedEvent event = new MatchResultCreatedEvent(
                request1UserId, request1Payload, request2UserId, request2Payload
        );

        handler.handle(event);

        verify(webSocketPublisher).publish(event);
    }

    private MatchResultResponse response(UUID partnerId) {
        return new MatchResultResponse(
                301L,
                MatchStatus.MATCHED,
                12L,
                null,
                new MatchProposalPartnerProfileResponse(
                        partnerId, "nickname", null, null, Set.of()
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
