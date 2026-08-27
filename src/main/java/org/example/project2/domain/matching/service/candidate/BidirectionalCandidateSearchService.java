package org.example.project2.domain.matching.service.candidate;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.candidate.BidirectionalMatchCandidate;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidirectionalCandidateSearchService {
    private static final Duration MAX_MEAL_TIME_DIFFERENCE = Duration.ofMinutes(30);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final MatchRequestRepository matchRequestRepository;

    /**
     * 요청을 보낸 사용자에게만, 양쪽 요청의 하드 필터를 모두 만족하는 후보를 반환합니다.
     * 성향 점수 계산과 프로필 제안 생성은 이 단계의 범위에 포함하지 않습니다.
     */
    public List<BidirectionalMatchCandidate> findCandidates(UUID userId, Long requestId) {
        if (userId == null || requestId == null) {
            throw new RealtimeMatchRequestException(RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND);
        }

        MatchRequest sourceRequest = matchRequestRepository.findOwnedById(requestId, userId)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND
                ));
        if (!sourceRequest.isWaiting()) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT,
                    "대기 중인 매칭 요청만 후보를 탐색할 수 있습니다."
            );
        }

        return matchRequestRepository.findWaitingCandidates(
                        MatchRequestStatus.WAITING,
                        sourceRequest.getId(),
                        userId
                ).stream()
                .filter(candidate -> isMutuallyEligible(sourceRequest, candidate))
                .map(candidate -> new BidirectionalMatchCandidate(
                        candidate.getId(),
                        candidate.getUser().getId(),
                        Math.toIntExact(Math.round(distanceMeters(sourceRequest.getLocation(), candidate.getLocation())))
                ))
                .sorted(Comparator.comparingInt(BidirectionalMatchCandidate::distanceMeters))
                .toList();
    }

    public boolean isMutuallyEligible(MatchRequest sourceRequest, MatchRequest candidateRequest) {
        return candidateRequest.isWaiting()
                && !sourceRequest.getUser().getId().equals(candidateRequest.getUser().getId())
                && satisfiesHardFilters(sourceRequest, candidateRequest)
                && satisfiesHardFilters(candidateRequest, sourceRequest);
    }

    private boolean satisfiesHardFilters(MatchRequest requester, MatchRequest candidate) {
        return requester.getFoodCategory().equals(candidate.getFoodCategory())
                && isWithinMealTimeDifference(requester, candidate)
                && isWithinRequestersSearchRadius(requester, candidate);
    }

    private boolean isWithinMealTimeDifference(MatchRequest first, MatchRequest second) {
        Duration difference = Duration.between(first.getMealAt(), second.getMealAt()).abs();
        return difference.compareTo(MAX_MEAL_TIME_DIFFERENCE) <= 0;
    }

    private boolean isWithinRequestersSearchRadius(MatchRequest requester, MatchRequest candidate) {
        Integer searchRadius = requester.getSearchRadius();
        return searchRadius != null
                && distanceMeters(requester.getLocation(), candidate.getLocation()) <= searchRadius;
    }

    private double distanceMeters(Point first, Point second) {
        double latitudeDifference = Math.toRadians(second.getY() - first.getY());
        double longitudeDifference = Math.toRadians(second.getX() - first.getX());
        double firstLatitude = Math.toRadians(first.getY());
        double secondLatitude = Math.toRadians(second.getY());

        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        double angularDistance = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return EARTH_RADIUS_METERS * angularDistance;
    }
}
