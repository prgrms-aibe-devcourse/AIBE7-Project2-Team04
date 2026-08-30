package org.example.project2.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.service.request.RealtimeMatchPrivacyCleanupService;
import org.example.project2.domain.region.repository.RegionRepository;
import org.example.project2.domain.user.dto.PreferredRegionResponse;
import org.example.project2.domain.user.dto.PreferredRegionUpdateRequest;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserLocationPreference;
import org.example.project2.domain.user.exception.LocationErrorCode;
import org.example.project2.domain.user.exception.LocationException;
import org.example.project2.domain.user.repository.UserLocationPreferenceRepository;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLocationPreferenceService {

    private final UserLocationPreferenceRepository userLocationPreferenceRepository;
    private final UserRepository userRepository;
    private final RealtimeMatchPrivacyCleanupService realtimeMatchPrivacyCleanupService;
    private final RegionRepository regionRepository;

    public PreferredRegionResponse getPreferredRegion(UUID userId) {
        return userLocationPreferenceRepository.findById(userId)
                .map(p -> new PreferredRegionResponse(p.getRegionCode(), p.getRegionName(), p.isLocationServiceConsent()))
                .orElse(null);
    }

    @Transactional
    public PreferredRegionResponse updatePreferredRegion(UUID userId, PreferredRegionUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!request.locationServiceConsent()) {
            realtimeMatchPrivacyCleanupService.removeActiveRequests(userId);
        }

        // [최종 검증] 행정구역 코드가 데이터베이스에 존재하는 유효한 영역인지 점검
        regionRepository.findById(request.regionCode())
                .orElseThrow(() -> new LocationException(LocationErrorCode.LOCATION_NOT_FOUND, "지원하지 않는 행정구역 코드입니다."));

        UserLocationPreference preference = userLocationPreferenceRepository.findById(userId)
                .map(p -> {
                    p.update(request.regionCode(), request.regionName(), request.locationServiceConsent());
                    return p;
                })
                .orElseGet(() -> {
                    UserLocationPreference newPreference = UserLocationPreference.builder()
                            .user(user)
                            .regionCode(request.regionCode())
                            .regionName(request.regionName())
                            .locationServiceConsent(request.locationServiceConsent())
                            .build();
                    return userLocationPreferenceRepository.save(newPreference);
                });

        return new PreferredRegionResponse(preference.getRegionCode(), preference.getRegionName(), preference.isLocationServiceConsent());
    }

    @Transactional
    public void deletePreferredRegion(UUID userId) {
        // 활성 요청의 정밀 핀과 희망 설명·임베딩을 Redis 상태와 함께 먼저 정리합니다.
        realtimeMatchPrivacyCleanupService.removeActiveRequests(userId);

        // 선호지역 및 위치동의 정보 물리 삭제
        if (userLocationPreferenceRepository.existsById(userId)) {
            userLocationPreferenceRepository.deleteById(userId);
        }
    }
}
