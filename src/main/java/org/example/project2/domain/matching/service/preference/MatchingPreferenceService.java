package org.example.project2.domain.matching.service.preference;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.preference.MatchingPreferenceItemRequest;
import org.example.project2.domain.matching.dto.preference.MatchingPreferenceItemResponse;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesResponse;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesUpdateRequest;
import org.example.project2.domain.matching.entity.UserMatchingPreference;
import org.example.project2.domain.matching.exception.preference.AuthenticatedMatchingUserNotFoundException;
import org.example.project2.domain.matching.repository.UserMatchingPreferenceRepository;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingPreferenceService {
    private final UserRepository userRepository;
    private final UserMatchingPreferenceRepository preferenceRepository;

    public MatchingPreferencesResponse getPreferences(UUID userId) {
        findUser(userId);
        return toResponse(preferenceRepository.findAllByUserId(userId));
    }

    @Transactional
    public MatchingPreferencesResponse replacePreferences(
            UUID userId,
            MatchingPreferencesUpdateRequest request
    ) {
        Map<PersonalityDimension, MatchingPreferenceItemRequest> validated =
                request.validatedPreferences();
        findUser(userId);

        preferenceRepository.deleteAllByUserId(userId);
        User user = userRepository.getReferenceById(userId);
        List<UserMatchingPreference> replacements = validated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> UserMatchingPreference.of(
                        user,
                        entry.getKey(),
                        (short) entry.getValue().importance(),
                        entry.getValue().mode()
                ))
                .toList();
        return toResponse(preferenceRepository.saveAll(replacements));
    }

    private User findUser(UUID userId) {
        if (userId == null) {
            throw new AuthenticatedMatchingUserNotFoundException();
        }
        return userRepository.findById(userId)
                .orElseThrow(AuthenticatedMatchingUserNotFoundException::new);
    }

    private MatchingPreferencesResponse toResponse(List<UserMatchingPreference> preferences) {
        List<MatchingPreferenceItemResponse> items = preferences.stream()
                .sorted(Comparator.comparing(preference -> preference.getId().getDimension()))
                .map(preference -> new MatchingPreferenceItemResponse(
                        preference.getId().getDimension(),
                        preference.getImportance(),
                        preference.getPreferenceMode()
                ))
                .toList();
        return new MatchingPreferencesResponse(items);
    }
}
