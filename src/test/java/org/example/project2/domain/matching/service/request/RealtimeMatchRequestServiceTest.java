package org.example.project2.domain.matching.service.request;

import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestCreateRequest;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.entity.MatchRequestStatus;
import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.matching.entity.UserMatchingPreference;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.matching.repository.RealtimeMatchWaitingStore;
import org.example.project2.domain.matching.repository.UserMatchingPreferenceRepository;
import org.example.project2.domain.matching.service.calculation.PersonalityCompatibilityCalculator;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityTag;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class RealtimeMatchRequestServiceTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final String REGION_CODE = "11680";

    @Autowired RealtimeMatchRequestService service;
    @Autowired UserRepository userRepository;
    @Autowired UserLocationPreferenceRepository locationPreferenceRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired UserMatchingPreferenceRepository matchingPreferenceRepository;
    @Autowired MatchRequestRepository matchRequestRepository;

    @MockitoBean RealtimeMatchWaitingStore waitingStore;
    @MockitoBean RegionPinValidator regionPinValidator;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        reset(waitingStore, regionPinValidator);
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
        for (PersonalityDimension dimension : PersonalityDimension.values()) {
            matchingPreferenceRepository.save(UserMatchingPreference.of(
                    user,
                    dimension,
                    (short) 3,
                    PreferenceMode.SIMILAR
            ));
        }
        when(regionPinValidator.validate(eq(REGION_CODE), any(Double.class), any(Double.class)))
                .thenReturn(RegionPinValidationResult.MATCHES);
        when(waitingStore.reserve(eq(user.getId()), any(String.class), any(Duration.class)))
                .thenReturn(true);
        when(waitingStore.activate(eq(user.getId()), any(String.class), anyLong(), any(Duration.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (user != null && userRepository.existsById(user.getId())) {
            matchRequestRepository.deleteAll(matchRequestRepository.findAllByUserId(user.getId()));
            matchingPreferenceRepository.deleteAll(
                    matchingPreferenceRepository.findAllByUserId(user.getId())
            );
            locationPreferenceRepository.deleteById(user.getId());
            userRepository.deleteById(user.getId());
        }
        if (otherUser != null && userRepository.existsById(otherUser.getId())) {
            userRepository.deleteById(otherUser.getId());
        }
    }

    @Test
    void createsWaitingRequestWithNormalizedRegionPointAndPreferenceSnapshot() {
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
        assertThat(saved.getPreferenceSnapshot()).isNotNull();
        assertThat(saved.getPreferenceSnapshot().dimensions()).hasSize(4);
        assertThat(saved.getDesiredPersonalityTags()).hasSize(3);
        verify(waitingStore).activate(
                eq(user.getId()), any(String.class), eq(saved.getId()), eq(Duration.ofMinutes(5))
        );
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
        return new RealtimeMatchRequestCreateRequest(
                FoodCategory.KOREAN,
                Instant.now().plusSeconds(3_600),
                REGION_CODE,
                "클라이언트 임의 표시명",
                "강남역 11번 출구",
                37.501,
                127.039,
                null,
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                desiredText
        );
    }
}
