package org.example.project2.domain.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPersonalityOnboardingStatusTest {

    @Test
    void changesPersonalityOnboardingStatus() {
        User user = User.builder()
                .email("user@test.com")
                .passwordHash("encoded-password")
                .nickname("test-user")
                .build();

        assertThat(user.getPersonalityOnboardingStatus())
                .isEqualTo(PersonalityOnboardingStatus.NOT_STARTED);

        user.skipPersonalityOnboarding();
        assertThat(user.getPersonalityOnboardingStatus())
                .isEqualTo(PersonalityOnboardingStatus.SKIPPED);

        user.completePersonalityOnboarding();
        assertThat(user.getPersonalityOnboardingStatus())
                .isEqualTo(PersonalityOnboardingStatus.COMPLETED);

        user.resetPersonalityOnboarding();
        assertThat(user.getPersonalityOnboardingStatus())
                .isEqualTo(PersonalityOnboardingStatus.NOT_STARTED);
    }
}
