package org.example.project2.domain.user.service;

import org.example.project2.domain.matching.service.request.RealtimeMatchPrivacyCleanupService;
import org.example.project2.domain.region.entity.Region;
import org.example.project2.domain.region.repository.RegionRepository;
import org.example.project2.domain.user.dto.PreferredRegionUpdateRequest;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserLocationPreference;
import org.example.project2.domain.user.repository.UserLocationPreferenceRepository;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLocationPreferenceServiceTest {
    @Mock UserLocationPreferenceRepository preferenceRepository;
    @Mock UserRepository userRepository;
    @Mock RealtimeMatchPrivacyCleanupService privacyCleanupService;
    @Mock RegionRepository regionRepository;

    private UserLocationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new UserLocationPreferenceService(
                preferenceRepository,
                userRepository,
                privacyCleanupService,
                regionRepository
        );
    }

    @Test
    void consentFalseUpdateRemovesActiveRealtimeMatchData() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@test.com").nickname("사용자").build();
        UserLocationPreference preference = UserLocationPreference.builder()
                .userId(userId)
                .user(user)
                .regionCode("11680")
                .regionName("서울특별시 강남구")
                .locationServiceConsent(true)
                .build();
        PreferredRegionUpdateRequest request = new PreferredRegionUpdateRequest(
                "11680", "서울특별시 강남구", false
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(regionRepository.findById("11680")).thenReturn(Optional.of(org.mockito.Mockito.mock(Region.class)));
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(preference));

        service.updatePreferredRegion(userId, request);

        verify(privacyCleanupService).removeActiveRequests(userId);
    }

    @Test
    void deletePreferenceRemovesActiveRealtimeMatchDataBeforePreference() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.existsById(userId)).thenReturn(true);

        service.deletePreferredRegion(userId);

        verify(privacyCleanupService).removeActiveRequests(userId);
        verify(preferenceRepository).deleteById(userId);
    }
}
