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
     * 요청자가 볼 수 있는 양방향 하드 필터 통과 후보만 반환합니다.
     * PostGIS 반경 1차 조회와 서비스 계층의 반대 방향 검증을 모두 통과한
     * 결과이며, 성향 계산이나 프로필 데이터 조립은 수행하지 않습니다.
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
                        Math.toIntExact(Math.round(distanceMeters(sourceRequest.getLocation(), candidate.getLocation()))),
                        candidate.getCreatedAt()
                ))
                .sorted(Comparator.comparingInt(BidirectionalMatchCandidate::distanceMeters))
                .toList();
    }

    public boolean isMutuallyEligible(MatchRequest sourceRequest, MatchRequest candidateRequest) {
        return sourceRequest.isWaiting()
                && candidateRequest.isWaiting()
                && !sourceRequest.getUser().getId().equals(candidateRequest.getUser().getId())
                && satisfiesHardFilters(sourceRequest, candidateRequest)
                && satisfiesHardFilters(candidateRequest, sourceRequest);
    }

    private boolean satisfiesHardFilters(MatchRequest requester, MatchRequest candidate) {
        // 음식 카테고리는 식사 장소·메뉴를 조율할 수 있는 선호 정보이므로
        // 후보 탈락 조건으로 사용하지 않습니다. 위치와 시간만 상호 검증합니다.
        return isWithinMealTimeDifference(requester, candidate)
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
