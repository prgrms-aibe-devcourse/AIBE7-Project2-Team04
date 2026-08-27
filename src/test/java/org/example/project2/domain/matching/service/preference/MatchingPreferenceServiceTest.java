package org.example.project2.domain.matching.service.preference;

import org.example.project2.domain.matching.dto.preference.MatchingPreferenceItemRequest;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesUpdateRequest;
import org.example.project2.domain.matching.entity.PreferenceMode;
import org.example.project2.domain.matching.exception.preference.AuthenticatedMatchingUserNotFoundException;
import org.example.project2.domain.matching.exception.preference.InvalidMatchingPreferenceInputException;
import org.example.project2.domain.matching.repository.UserMatchingPreferenceRepository;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MatchingPreferenceServiceTest {
    @Autowired MatchingPreferenceService matchingPreferenceService;
    @Autowired UserMatchingPreferenceRepository preferenceRepository;
    @Autowired UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        user = userRepository.save(User.builder()
                .email("matching-preference-" + suffix + "@test.com")
                .passwordHash("hashed")
                .nickname("matching-preference-" + suffix)
                .build());
    }

    @Test
    void returnsEmptyListWhenUserHasNoPreferences() {
        assertThat(matchingPreferenceService.getPreferences(user.getId()).preferences()).isEmpty();
    }

    @Test
    void replacesAllFourPreferencesAsSingleSet() {
        var first = matchingPreferenceService.replacePreferences(user.getId(), request(5, PreferenceMode.SIMILAR));
        var second = matchingPreferenceService.replacePreferences(user.getId(), request(1, PreferenceMode.COMPLEMENTARY));

        assertThat(first.preferences()).hasSize(4);
        assertThat(second.preferences()).hasSize(4)
                .allSatisfy(preference -> {
                    assertThat(preference.importance()).isEqualTo((short) 1);
                    assertThat(preference.mode()).isEqualTo(PreferenceMode.COMPLEMENTARY);
                });
        assertThat(preferenceRepository.findAllByUserId(user.getId())).hasSize(4);
    }

    @Test
    void validatesCompleteUniqueSetBeforeDeletingExistingPreferences() {
        matchingPreferenceService.replacePreferences(user.getId(), request(3, PreferenceMode.SIMILAR));
        MatchingPreferencesUpdateRequest duplicate = new MatchingPreferencesUpdateRequest(List.of(
                item(PersonalityDimension.CONVERSATION_LEVEL, 5, PreferenceMode.SIMILAR),
                item(PersonalityDimension.CONVERSATION_LEVEL, 4, PreferenceMode.SIMILAR),
                item(PersonalityDimension.PLANNING_STYLE, 2, PreferenceMode.COMPLEMENTARY),
                item(PersonalityDimension.NOVELTY_PREFERENCE, 3, PreferenceMode.SIMILAR)
        ));

        assertThatThrownBy(() -> matchingPreferenceService.replacePreferences(user.getId(), duplicate))
                .isInstanceOf(InvalidMatchingPreferenceInputException.class);
        assertThat(preferenceRepository.findAllByUserId(user.getId())).hasSize(4);
    }

    @Test
    void rejectsUnknownAuthenticatedUser() {
        assertThatThrownBy(() -> matchingPreferenceService.getPreferences(UUID.randomUUID()))
                .isInstanceOf(AuthenticatedMatchingUserNotFoundException.class);
    }

    private MatchingPreferencesUpdateRequest request(int importance, PreferenceMode mode) {
        return new MatchingPreferencesUpdateRequest(List.of(
                item(PersonalityDimension.CONVERSATION_LEVEL, importance, mode),
                item(PersonalityDimension.MEAL_PACE, importance, mode),
                item(PersonalityDimension.PLANNING_STYLE, importance, mode),
                item(PersonalityDimension.NOVELTY_PREFERENCE, importance, mode)
        ));
    }

    private MatchingPreferenceItemRequest item(
            PersonalityDimension dimension,
            int importance,
            PreferenceMode mode
    ) {
        return new MatchingPreferenceItemRequest(dimension, importance, mode);
    }
}
