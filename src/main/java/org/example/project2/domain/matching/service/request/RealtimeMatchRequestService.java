package org.example.project2.domain.matching.service.request;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestCreateRequest;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestResponse;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestStatusResponse;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.exception.request.AuthenticatedRealtimeMatchUserNotFoundException;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.matching.service.request.embedding.DesiredPersonalityEmbeddingRequestedEvent;
import org.example.project2.domain.matching.service.proposal.RealtimeMatchRequestWaitingEvent;
import org.example.project2.domain.matching.service.proposal.MatchProposalLifecycleService;
import org.example.project2.domain.region.entity.Region;
import org.example.project2.domain.region.repository.RegionRepository;
import org.example.project2.domain.region.service.RegionPinValidationResult;
import org.example.project2.domain.region.service.RegionPinValidator;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserLocationPreference;
import org.example.project2.domain.user.repository.UserLocationPreferenceRepository;
import org.example.project2.domain.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeMatchRequestService {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final List<MatchRequestStatus> ACTIVE_STATUSES =
            List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING);

    private final UserRepository userRepository;
    private final UserLocationPreferenceRepository locationPreferenceRepository;
    private final RegionRepository regionRepository;
    private final RegionPinValidator regionPinValidator;
    private final MatchRequestRepository matchRequestRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final MatchProposalLifecycleService matchProposalLifecycleService;
    private final RealtimeMatchWaitingStore waitingStore;
    private final MatchingProperties matchingProperties;
    private final RealtimeMatchWaitingReconciliationService waitingReconciliationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RealtimeMatchRequestResponse create(
            UUID userId,
            RealtimeMatchRequestCreateRequest request
    ) {
        User user = findUser(userId);
        UserLocationPreference locationPreference = requireLocationConsent(userId);
        Region region = normalizeRegion(request.regionCode(), locationPreference);
        validatePin(region.getRegionCode(), request.longitude(), request.latitude());

        String reservationToken = UUID.randomUUID().toString();
        Duration ttl = matchingProperties.waitingTtl();
        reserveWaitingSlot(userId, reservationToken, ttl);

        Long savedRequestId = null;
        try {
            if (matchRequestRepository.existsByUserIdAndStatusIn(
                    userId,
                    List.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING)
            )) {
                throw new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.ACTIVE_REQUEST_EXISTS
                );
            }

            Point location = createPoint(request.longitude(), request.latitude());
            int searchRadius = request.searchRadius() == null
                    ? matchingProperties.defaultSearchRadiusMeters()
                    : request.searchRadius();
            MatchRequest matchRequest = MatchRequest.create(
                    user,
                    request.foodCategory().name(),
                    request.desiredTimeSlot(),
                    region.getRegionCode(),
                    region.getFullName(),
                    request.locationName(),
                    location,
                    searchRadius,
                    request.desiredPersonalityTags(),
                    request.desiredPersonalityText(),
                    PersonalityCompatibilityCalculator.FORMULA_VERSION
            );
            MatchRequest saved = matchRequestRepository.saveAndFlush(matchRequest);
            savedRequestId = saved.getId();
            activateWaitingSlot(userId, reservationToken, saved.getId(), saved.getLocation(), ttl);
            registerRollbackCompensation(userId, saved.getId());

            if (saved.getDesiredPersonalityText() != null) {
                eventPublisher.publishEvent(new DesiredPersonalityEmbeddingRequestedEvent(
                        saved.getId(),
                        saved.getDesiredPersonalityText()
                ));
            }
            eventPublisher.publishEvent(new RealtimeMatchRequestWaitingEvent(userId, saved.getId()));
            Instant expiresAt = Instant.now().plus(ttl);
            return new RealtimeMatchRequestResponse(
                    saved.getId(),
                    saved.getStatus(),
                    expiresAt
            );
        } catch (RuntimeException exception) {
            if (savedRequestId != null) {
                removeWaitingSlot(userId, savedRequestId);
            }
            releaseReservation(userId, reservationToken);
            throw exception;
        }
    }

    public RealtimeMatchRequestStatusResponse getCurrent(UUID userId) {
        findUser(userId);
        MatchRequest request = matchRequestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND
                ));
        TtlLookup ttlLookup = remainingTtl(request.getId());
        if (request.isWaiting() && ttlLookup.redisAvailable() && ttlLookup.remaining().isEmpty()) {
            RealtimeMatchWaitingReconciliationService.RepairResult result =
                    waitingReconciliationService.repair(request.getId());
            if (result == RealtimeMatchWaitingReconciliationService.RepairResult.EXPIRED) {
                throw new RealtimeMatchRequestException(RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND);
            }
            ttlLookup = remainingTtl(request.getId());
        }
        Instant expiresAt = ttlLookup.remaining().map(duration -> Instant.now().plus(duration)).orElse(null);
        return new RealtimeMatchRequestStatusResponse(
                request.getId(),
                request.getStatus(),
                expiresAt
        );
    }

    @Transactional
    public void cancel(UUID userId, Long requestId) {
        findUser(userId);
        if (requestId == null) {
            throw new RealtimeMatchRequestException(RealtimeMatchRequestErrorCode.INVALID_INPUT);
        }
        // 제안이 있는 경우 제안 서비스가 제안 행 → 요청 ID 오름차순으로 잠근다.
        // 여기서 요청을 먼저 잠그면 취소와 수락 경로의 잠금 순서가 달라져 교착될 수 있으므로,
        // 먼저 소유권만 확인하고 제안이 없을 때에만 요청 행을 잠근다.
        MatchRequest request = matchRequestRepository.findOwnedById(requestId, userId)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND
                ));
        Optional<MatchProposal> pendingProposal = matchProposalRepository.findPendingByRequestId(requestId);
        if (pendingProposal.isPresent()) {
            try {
                matchProposalLifecycleService.cancelForRequest(pendingProposal.get().getId(), requestId);
            } catch (IllegalStateException exception) {
                throw new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT,
                        exception.getMessage()
                );
            }
            return;
        }
        request = matchRequestRepository.findOwnedByIdForUpdate(requestId, userId)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND
                ));
        // 최초 조회와 요청 행 잠금 사이에 제안이 생성될 수 있으므로 마지막으로 재확인한다.
        // 이 시점에 제안이 보이면 요청을 먼저 잠근 채 제안 잠금을 시도하지 않고
        // 충돌로 종료하여 수락 경로와의 교착 및 고아 CONFIRMING 요청을 방지한다.
        if (matchProposalRepository.findPendingByRequestId(requestId).isPresent()) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT,
                    "매칭 제안이 생성되어 취소할 수 없습니다. 제안 응답을 먼저 처리해 주세요."
            );
        }
        try {
            request.cancel();
        } catch (IllegalStateException exception) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT,
                    exception.getMessage()
            );
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeWaitingSlot(userId, requestId);
            }
        });
    }

    private User findUser(UUID userId) {
        if (userId == null) {
            throw new AuthenticatedRealtimeMatchUserNotFoundException();
        }
        return userRepository.findById(userId)
                .orElseThrow(AuthenticatedRealtimeMatchUserNotFoundException::new);
    }

    private UserLocationPreference requireLocationConsent(UUID userId) {
        UserLocationPreference preference = locationPreferenceRepository.findById(userId)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.LOCATION_CONSENT_REQUIRED
                ));
        if (!preference.isLocationServiceConsent()) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.LOCATION_CONSENT_REQUIRED
            );
        }
        return preference;
    }

    private Region normalizeRegion(
            String requestedRegionCode,
            UserLocationPreference locationPreference
    ) {
        if (!locationPreference.getRegionCode().equals(requestedRegionCode)) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.INVALID_INPUT,
                    "기본 활동지역과 동일한 행정구역에서만 매칭을 요청할 수 있습니다."
            );
        }
        return regionRepository.findById(requestedRegionCode)
                .orElseThrow(() -> new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.INVALID_INPUT,
                        "지원하지 않는 행정구역 코드입니다."
                ));
    }

    private void validatePin(String regionCode, double longitude, double latitude) {
        RegionPinValidationResult result = regionPinValidator.validate(regionCode, longitude, latitude);
        if (result == RegionPinValidationResult.OUTSIDE) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.INVALID_INPUT,
                    "선택한 위치가 요청한 행정구역에 속하지 않습니다."
            );
        }
        if (result == RegionPinValidationResult.UNAVAILABLE) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.REGION_VALIDATION_UNAVAILABLE
            );
        }
    }

    private Point createPoint(double longitude, double latitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }

    private void reserveWaitingSlot(UUID userId, String token, Duration ttl) {
        try {
            if (!waitingStore.reserve(userId, token, ttl)) {
                throw new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.ACTIVE_REQUEST_EXISTS
                );
            }
        } catch (DataAccessException exception) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.WAITING_STORE_UNAVAILABLE
            );
        }
    }

    private void activateWaitingSlot(
            UUID userId,
            String token,
            long requestId,
            Point location,
            Duration ttl
    ) {
        try {
            if (!waitingStore.activate(
                    userId,
                    token,
                    requestId,
                    location.getX(),
                    location.getY(),
                    ttl
            )) {
                throw new RealtimeMatchRequestException(
                        RealtimeMatchRequestErrorCode.WAITING_STORE_UNAVAILABLE
                );
            }
        } catch (DataAccessException exception) {
            throw new RealtimeMatchRequestException(
                    RealtimeMatchRequestErrorCode.WAITING_STORE_UNAVAILABLE
            );
        }
    }

    private void releaseReservation(UUID userId, String token) {
        try {
            waitingStore.releaseReservation(userId, token);
        } catch (DataAccessException ignored) {
            // 예약 TTL이 남은 경우 자동 만료되며 원래 예외를 우선 전달합니다.
        }
    }

    private void registerRollbackCompensation(UUID userId, long requestId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    removeWaitingSlot(userId, requestId);
                }
            }
        });
    }

    private void removeWaitingSlot(UUID userId, long requestId) {
        try {
            waitingStore.remove(userId, requestId);
        } catch (DataAccessException ignored) {
            // Redis 장애 시 키 TTL 만료에 맡기고 DB의 상태 전이를 유지합니다.
        }
    }

    private TtlLookup remainingTtl(long requestId) {
        try {
            Optional<Duration> remaining = Optional.ofNullable(waitingStore.remainingTtl(requestId))
                    .orElse(Optional.empty());
            return new TtlLookup(remaining, true);
        } catch (DataAccessException ignored) {
            return new TtlLookup(Optional.empty(), false);
        }
    }

    private record TtlLookup(Optional<Duration> remaining, boolean redisAvailable) {
    }
}
