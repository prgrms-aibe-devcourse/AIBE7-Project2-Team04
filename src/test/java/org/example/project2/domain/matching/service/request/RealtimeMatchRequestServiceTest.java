package org.example.project2.domain.matching.service.request;

import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestCreateRequest;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.region.entity.Region;
import org.example.project2.domain.region.repository.RegionRepository;
import org.example.project2.domain.region.service.RegionPinValidationResult;
import org.example.project2.domain.region.service.RegionPinValidator;
import org.example.project2.domain.user.entity.FoodCategory;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserLocationPreference;
import org.example.project2.domain.user.repository.UserLocationPreferenceRepository;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class RealtimeMatchRequestServiceTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final String REGION_CODE = "11680";
    private static final String DIFFERENT_REGION_CODE = "11740";
    private static final String UNSUPPORTED_REGION_CODE = "99999";

    @Autowired RealtimeMatchRequestService service;
    @Autowired UserRepository userRepository;
    @Autowired UserLocationPreferenceRepository locationPreferenceRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired MatchRequestRepository matchRequestRepository;
    @Autowired MatchProposalRepository matchProposalRepository;

    @MockitoBean RealtimeMatchWaitingStore waitingStore;
    @MockitoBean RegionPinValidator regionPinValidator;
    @MockitoBean PersonalityAiClient aiClient;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        reset(waitingStore, regionPinValidator, aiClient);
        when(aiClient.embeddingModelName()).thenReturn("integration-embedding-model");
        String suffix = UUID.randomUUID().toString();
        user = userRepository.save(User.builder()
                .email("realtime-request-" + suffix + "@test.com")
                .passwordHash("hashed")
                .nickname("realtime-request-" + suffix)
                .build());
        var center = GEOMETRY_FACTORY.createPoint(new Coordinate(127.047, 37.517));
        center.setSRID(4326);
        regionRepository.save(new Region(
                REGION_CODE,
                "서울특별시",
                "강남구",
                "서울특별시 강남구",
                center
        ));
        locationPreferenceRepository.save(UserLocationPreference.builder()
                .user(user)
                .regionCode(REGION_CODE)
                .regionName("서울특별시 강남구")
                .locationServiceConsent(true)
                .build());
        when(regionPinValidator.validate(eq(REGION_CODE), any(Double.class), any(Double.class)))
                .thenReturn(RegionPinValidationResult.MATCHES);
        when(waitingStore.reserve(eq(user.getId()), any(String.class), any(Duration.class)))
                .thenReturn(true);
        when(waitingStore.activate(
                eq(user.getId()), any(String.class), anyLong(), anyDouble(), anyDouble(), any(Duration.class)
        ))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (user != null && userRepository.existsById(user.getId())) {
            matchProposalRepository.deleteAllInBatch();
            matchRequestRepository.deleteAll(matchRequestRepository.findAllByUserId(user.getId()));
            locationPreferenceRepository.deleteById(user.getId());
            userRepository.deleteById(user.getId());
        }
        if (otherUser != null && userRepository.existsById(otherUser.getId())) {
            userRepository.deleteById(otherUser.getId());
        }
    }

    @Test
    void createsWaitingRequestWithNormalizedRegionPointAndDesiredPersonality() {
        var response = service.create(user.getId(), validRequest(null));

        MatchRequest saved = matchRequestRepository.findDetailedById(response.requestId()).orElseThrow();
        assertThat(response.status()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(response.expiresAt()).isAfter(Instant.now().plusSeconds(250));
        assertThat(saved.getRegionName()).isEqualTo("서울특별시 강남구");
        assertThat(saved.getLocation().getX()).isEqualTo(127.039);
        assertThat(saved.getLocation().getY()).isEqualTo(37.501);
        assertThat(saved.getLocation().getSRID()).isEqualTo(4326);
        assertThat(saved.getSearchRadius()).isEqualTo(3_000);
        assertThat(saved.getMatchingFormulaVersion())
                .isEqualTo(PersonalityCompatibilityCalculator.FORMULA_VERSION);
        assertThat(saved.getDesiredPersonalityTags()).hasSize(3);
        verify(waitingStore).activate(
                eq(user.getId()), any(String.class), eq(saved.getId()),
                eq(saved.getLocation().getX()), eq(saved.getLocation().getY()), eq(Duration.ofMinutes(5))
        );
    }

    @Test
    void createsWaitingRequestWhenRequestedRegionDiffersFromPreferredRegion() {
        var center = GEOMETRY_FACTORY.createPoint(new Coordinate(127.1238, 37.5301));
        center.setSRID(4326);
        regionRepository.save(new Region(
                DIFFERENT_REGION_CODE,
                "서울특별시",
                "강동구",
                "서울특별시 강동구",
                center
        ));
        when(regionPinValidator.validate(eq(DIFFERENT_REGION_CODE), anyDouble(), anyDouble()))
                .thenReturn(RegionPinValidationResult.MATCHES);

        var response = service.create(
                user.getId(),
                validRequest(DIFFERENT_REGION_CODE, 37.5301, 127.1238, null)
        );

        MatchRequest saved = matchRequestRepository.findDetailedById(response.requestId()).orElseThrow();
        assertThat(response.status()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(saved.getRegionCode()).isEqualTo(DIFFERENT_REGION_CODE);
        assertThat(saved.getRegionName()).isEqualTo("서울특별시 강동구");
        assertThat(saved.getLocation().getX()).isEqualTo(127.1238);
        assertThat(saved.getLocation().getY()).isEqualTo(37.5301);
        assertThat(locationPreferenceRepository.findById(user.getId()).orElseThrow().getRegionCode())
                .isEqualTo(REGION_CODE);
        verify(regionPinValidator).validate(DIFFERENT_REGION_CODE, 127.1238, 37.5301);
    }

    @Test
    void savesDesiredTextEmbeddingAsynchronouslyAfterRequestSave() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        String desiredText = "편안하게 대화하는 분";
        when(aiClient.embed(desiredText)).thenReturn(Optional.of(vector));

        var response = service.create(user.getId(), validRequest(desiredText));

        MatchRequest saved = awaitDesiredEmbedding(response.requestId());

        assertThat(response.status()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(saved.getDesiredPersonalityText()).isEqualTo(desiredText);
        assertThat(saved.getDesiredPersonalityEmbedding()).hasSize(1536);
        assertThat(saved.getDesiredPersonalityEmbedding()[0]).isEqualTo(1.0f);
        assertThat(saved.getEmbeddingModel()).isEqualTo("integration-embedding-model");
        assertThat(saved.getEmbeddingVersion()).isEqualTo("PERSONALITY_FREE_TEXT_V2");
        assertThat(saved.getEmbeddedAt()).isNotNull();
        verify(aiClient).embed(desiredText);
    }

    @Test
    void keepsWaitingRequestAndTagFallbackWhenDesiredEmbeddingFails() {
        String desiredText = "AI 장애가 나도 요청은 대기해야 해요.";
        when(aiClient.embed(desiredText)).thenReturn(Optional.empty());

        var response = service.create(user.getId(), validRequest(desiredText));

        MatchRequest saved = matchRequestRepository.findById(response.requestId()).orElseThrow();
        assertThat(response.status()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(saved.getDesiredPersonalityEmbedding()).isNull();
        assertThat(saved.getEmbeddingModel()).isNull();
        assertThat(saved.getEmbeddingVersion()).isNull();
        assertThat(saved.getEmbeddedAt()).isNull();
        verify(aiClient, timeout(5_000)).embed(desiredText);
    }

    @Test
    void rejectsDuplicateWaitingRequestAtAtomicReservation() {
        when(waitingStore.reserve(eq(user.getId()), any(String.class), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(user.getId(), validRequest(null)))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.ACTIVE_REQUEST_EXISTS));

        assertThat(matchRequestRepository.findAllByUserIdAndStatusIn(
                user.getId(),
                Set.of(MatchRequestStatus.WAITING).stream().toList()
        )).isEmpty();
    }

    @Test
    void compensatesDatabaseAndRedisReservationWhenActivationFails() {
        when(waitingStore.activate(
                eq(user.getId()), any(String.class), anyLong(), anyDouble(), anyDouble(), any(Duration.class)
        )).thenReturn(false);

        assertThatThrownBy(() -> service.create(user.getId(), validRequest(null)))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.WAITING_STORE_UNAVAILABLE));

        assertThat(matchRequestRepository.findAllByUserIdAndStatusIn(
                user.getId(),
                Set.of(MatchRequestStatus.WAITING, MatchRequestStatus.CONFIRMING).stream().toList()
        )).isEmpty();
        verify(waitingStore).releaseReservation(eq(user.getId()), any(String.class));
        verify(waitingStore).remove(eq(user.getId()), anyLong());
    }

    @Test
    void rejectsRequestWithoutLocationConsentBeforeRedisReservation() {
        UserLocationPreference preference = locationPreferenceRepository.findById(user.getId()).orElseThrow();
        preference.update(REGION_CODE, "서울특별시 강남구", false);
        locationPreferenceRepository.save(preference);

        assertThatThrownBy(() -> service.create(user.getId(), validRequest(null)))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.LOCATION_CONSENT_REQUIRED));

        verify(waitingStore, never()).reserve(any(), any(), any());
    }

    @Test
    void rejectsPinOutsideSelectedRegion() {
        when(regionPinValidator.validate(eq(REGION_CODE), any(Double.class), any(Double.class)))
                .thenReturn(RegionPinValidationResult.OUTSIDE);

        assertThatThrownBy(() -> service.create(user.getId(), validRequest(null)))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.INVALID_INPUT));
        verify(waitingStore, never()).reserve(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedRequestedRegionBeforeRedisReservation() {
        assertThatThrownBy(() -> service.create(
                user.getId(),
                validRequest(UNSUPPORTED_REGION_CODE, 37.501, 127.039, null)
        ))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.INVALID_INPUT));

        verify(regionPinValidator, never()).validate(any(), anyDouble(), anyDouble());
        verify(waitingStore, never()).reserve(any(), any(), any());
    }

    @Test
    void getsCurrentRequestAndCancelsOnlyOwnedRequest() {
        var created = service.create(user.getId(), validRequest(null));
        when(waitingStore.remainingTtl(created.requestId()))
                .thenReturn(Optional.of(Duration.ofMinutes(4)));

        var current = service.getCurrent(user.getId());
        service.cancel(user.getId(), created.requestId());

        assertThat(current.requestId()).isEqualTo(created.requestId());
        assertThat(current.expiresAt()).isNotNull();
        assertThat(matchRequestRepository.findById(created.requestId()).orElseThrow().getStatus())
                .isEqualTo(MatchRequestStatus.CANCELLED);
        verify(waitingStore).remove(user.getId(), created.requestId());
    }

    @Test
    void repairsMissingRedisWaitingEntryBeforeReturningCurrentStatus() {
        var created = service.create(user.getId(), validRequest(null));
        when(waitingStore.remainingTtl(created.requestId()))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(Duration.ofMinutes(4)));
        when(waitingStore.restore(
                eq(user.getId()), eq(created.requestId()), anyDouble(), anyDouble(), any(Duration.class)
        )).thenReturn(true);

        var current = service.getCurrent(user.getId());

        assertThat(current.requestId()).isEqualTo(created.requestId());
        assertThat(current.status()).isEqualTo(MatchRequestStatus.WAITING);
        assertThat(current.expiresAt()).isNotNull();
        verify(waitingStore).restore(
                eq(user.getId()), eq(created.requestId()), eq(127.039), eq(37.501), any(Duration.class)
        );
    }

    @Test
    void hidesAnotherUsersRequestDuringCancellation() {
        var created = service.create(user.getId(), validRequest(null));
        String suffix = UUID.randomUUID().toString();
        otherUser = userRepository.save(User.builder()
                .email("other-realtime-request-" + suffix + "@test.com")
                .passwordHash("hashed")
                .nickname("other-realtime-request-" + suffix)
                .build());
        reset(waitingStore);

        assertThatThrownBy(() -> service.cancel(otherUser.getId(), created.requestId()))
                .isInstanceOfSatisfying(RealtimeMatchRequestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND));

        assertThat(matchRequestRepository.findById(created.requestId()).orElseThrow().getStatus())
                .isEqualTo(MatchRequestStatus.WAITING);
        verify(waitingStore, never()).remove(any(), anyLong());
    }

    private RealtimeMatchRequestCreateRequest validRequest(String desiredText) {
        return validRequest(REGION_CODE, 37.501, 127.039, desiredText);
    }

    private RealtimeMatchRequestCreateRequest validRequest(
            String regionCode,
            double latitude,
            double longitude,
            String desiredText
    ) {
        return new RealtimeMatchRequestCreateRequest(
                FoodCategory.KOREAN,
                Instant.now().plusSeconds(3_600),
                regionCode,
                "클라이언트 임의 표시명",
                "강남역 11번 출구",
                latitude,
                longitude,
                null,
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                desiredText
        );
    }

    private MatchRequest awaitDesiredEmbedding(Long requestId) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            MatchRequest request = matchRequestRepository.findById(requestId).orElseThrow();
            if (request.getDesiredPersonalityEmbedding() != null) {
                return request;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new AssertionError("비동기 희망 텍스트 임베딩을 기다리는 중 인터럽트가 발생했습니다.", interruptedException);
            }
        }
        throw new AssertionError("매칭 요청 저장 후 5초 안에 희망 텍스트 임베딩이 저장되지 않았습니다.");
    }
}
