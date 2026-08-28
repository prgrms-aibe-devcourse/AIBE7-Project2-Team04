package org.example.project2.domain.matching.service.candidate;

import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidirectionalCandidateSearchServiceTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant MEAL_AT = Instant.parse("2026-08-27T10:00:00Z");

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @InjectMocks
    private BidirectionalCandidateSearchService service;

    @Test
    void searchesOnlyWaitingRequestsFromOtherUsers() {
        User sourceUser = user("source");
        MatchRequest source = request(1L, sourceUser, MEAL_AT, "KOREAN", 2_000, 127.000, 37.500);
        MatchRequest waitingCandidate = request(2L, user("waiting"), MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);
        MatchRequest sameUserRequest = request(3L, sourceUser, MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);
        MatchRequest cancelledCandidate = request(4L, user("cancelled"), MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);
        cancelledCandidate.cancel();
        MatchRequest expiredCandidate = request(5L, user("expired"), MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);
        expiredCandidate.expire();
        MatchRequest confirmingCandidate = request(6L, user("confirming"), MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);
        confirmingCandidate.startConfirming();

        candidatesFor(source, List.of(
                waitingCandidate,
                sameUserRequest,
                cancelledCandidate,
                expiredCandidate,
                confirmingCandidate
        ));

        List<BidirectionalMatchCandidate> result = service.findCandidates(sourceUser.getId(), source.getId());

        assertThat(result).extracting(BidirectionalMatchCandidate::requestId)
                .containsExactly(waitingCandidate.getId());
        verify(matchRequestRepository).findWaitingCandidates(any(), eq(source.getId()), eq(sourceUser.getId()));
    }

    @Test
    void requiresDistanceToBeWithinBothRequestsSearchRadius() {
        User sourceUser = user("source");
        MatchRequest source = request(1L, sourceUser, MEAL_AT, "KOREAN", 2_000, 127.000, 37.500);
        MatchRequest sourceOnlyDistanceMatch = request(
                2L, user("small-radius"), MEAL_AT, "KOREAN", 500, 127.010, 37.500
        );
        MatchRequest mutualDistanceMatch = request(
                3L, user("mutual-radius"), MEAL_AT, "KOREAN", 2_000, 127.010, 37.500
        );

        candidatesFor(source, List.of(sourceOnlyDistanceMatch, mutualDistanceMatch));

        List<BidirectionalMatchCandidate> result = service.findCandidates(sourceUser.getId(), source.getId());

        assertThat(result).extracting(BidirectionalMatchCandidate::requestId)
                .containsExactly(mutualDistanceMatch.getId());
    }

    @Test
    void excludesCandidatesOutsideMealTimeWindowButAllowsAnotherFoodCategory() {
        User sourceUser = user("source");
        MatchRequest source = request(1L, sourceUser, MEAL_AT, "KOREAN", 2_000, 127.000, 37.500);
        MatchRequest timeMismatch = request(
                2L, user("time"), MEAL_AT.plusSeconds(30 * 60 + 1), "KOREAN", 2_000, 127.005, 37.500
        );
        MatchRequest foodMismatch = request(
                3L, user("food"), MEAL_AT, "JAPANESE", 2_000, 127.005, 37.500
        );
        MatchRequest valid = request(
                4L, user("valid"), MEAL_AT.plusSeconds(30 * 60), "KOREAN", 2_000, 127.005, 37.500
        );

        candidatesFor(source, List.of(timeMismatch, foodMismatch, valid));

        List<BidirectionalMatchCandidate> result = service.findCandidates(sourceUser.getId(), source.getId());

        assertThat(result).extracting(BidirectionalMatchCandidate::requestId)
                .containsExactlyInAnyOrder(foodMismatch.getId(), valid.getId());
    }

    @Test
    void rejectsCandidateSearchForANonWaitingSourceRequest() {
        User sourceUser = user("source");
        MatchRequest source = request(1L, sourceUser, MEAL_AT, "KOREAN", 2_000, 127.000, 37.500);
        source.startConfirming();
        when(matchRequestRepository.findOwnedById(source.getId(), sourceUser.getId()))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.findCandidates(sourceUser.getId(), source.getId()))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT));
    }

    @Test
    void requiresBothRequestsToBeWaitingForMutualEligibility() {
        MatchRequest source = request(1L, user("source"), MEAL_AT, "KOREAN", 2_000, 127.000, 37.500);
        MatchRequest candidate = request(2L, user("candidate"), MEAL_AT, "KOREAN", 2_000, 127.005, 37.500);

        source.startConfirming();

        assertThat(service.isMutuallyEligible(source, candidate)).isFalse();
    }

    private void candidatesFor(MatchRequest source, List<MatchRequest> candidates) {
        when(matchRequestRepository.findOwnedById(source.getId(), source.getUser().getId()))
                .thenReturn(Optional.of(source));
        when(matchRequestRepository.findWaitingCandidates(any(), eq(source.getId()), eq(source.getUser().getId())))
                .thenReturn(candidates);
    }

    private MatchRequest request(
            Long id,
            User user,
            Instant mealAt,
            String foodCategory,
            int searchRadius,
            double longitude,
            double latitude
    ) {
        MatchRequest request = MatchRequest.create(
                user,
                foodCategory,
                mealAt,
                "11680",
                "서울특별시 강남구",
                "테스트 장소",
                point(longitude, latitude),
                searchRadius,
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                null,
                "DESIRED_PERSONALITY_MATCH_V1"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private User user(String label) {
        String suffix = label + "-" + UUID.randomUUID();
        return User.builder()
                .id(UUID.randomUUID())
                .email(suffix + "@test.com")
                .passwordHash("hashed")
                .nickname(suffix)
                .build();
    }

    private org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        org.locationtech.jts.geom.Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
