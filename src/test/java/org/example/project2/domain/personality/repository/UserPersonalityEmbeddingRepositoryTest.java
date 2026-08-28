package org.example.project2.domain.personality.repository;

import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserPersonalityEmbeddingRepositoryTest {
    @Autowired UserRepository userRepository;
    @Autowired UserPersonalityProfileRepository profileRepository;
    @Autowired UserPersonalityEmbeddingRepository embeddingRepository;

    @Test
    void bindsFloatArrayAsPostgresqlVector() {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .email("embedding-" + suffix + "@test.com")
                .passwordHash("hashed")
                .nickname("embedding-" + suffix)
                .build());
        UserPersonalityProfile profile = profileRepository.save(UserPersonalityProfile.builder()
                .user(user)
                .questionnaireVersion(PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1)
                .conversationLevel((short) 50)
                .mealPace((short) 50)
                .planningStyle((short) 50)
                .noveltyPreference((short) 50)
                .aiAnalysisConsent(true)
                .selfDescription("테스트 설명")
                .completedAt(Instant.now())
                .build());
        UserPersonalityEmbedding embedding = UserPersonalityEmbedding.builder()
                .profile(profile)
                .build();
        embedding.replace("versioned test document", new float[1536],
                "test-model", "PERSONALITY_FREE_TEXT_V2");

        embeddingRepository.saveAndFlush(embedding);

        assertThat(embeddingRepository.findById(user.getId())).isPresent();
        assertThat(profileRepository.findAllByUserIdIn(List.of(user.getId())))
                .extracting(UserPersonalityProfile::getUserId)
                .containsExactly(user.getId());
        assertThat(embeddingRepository.findAllByUserIdIn(List.of(user.getId())))
                .extracting(UserPersonalityEmbedding::getUserId)
                .containsExactly(user.getId());
    }
}
