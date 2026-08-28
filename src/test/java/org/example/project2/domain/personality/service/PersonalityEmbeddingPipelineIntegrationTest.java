package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.dto.PersonalityAnswerRequest;
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
import org.example.project2.domain.personality.entity.PersonalityAnswerValue;
import org.example.project2.domain.personality.entity.PersonalityDimension;
import org.example.project2.domain.personality.entity.PersonalityQuestionnaireVersion;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityEmbedding;
import org.example.project2.domain.personality.repository.UserPersonalityAnswerRepository;
import org.example.project2.domain.personality.repository.UserPersonalityEmbeddingRepository;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.personality.service.ai.PersonalityAiClient;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 프로필 저장 트랜잭션과 AFTER_COMMIT 임베딩 리스너를 함께 검증합니다.
 */
@SpringBootTest
class PersonalityEmbeddingPipelineIntegrationTest {
    @Autowired PersonalityService personalityService;
    @Autowired UserRepository userRepository;
    @Autowired UserPersonalityProfileRepository profileRepository;
    @Autowired UserPersonalityAnswerRepository answerRepository;
    @Autowired UserPersonalityEmbeddingRepository embeddingRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean PersonalityAiClient aiClient;

    private User user;

    @BeforeEach
    void setUp() {
        reset(aiClient);
        when(aiClient.embeddingModelName()).thenReturn("integration-embedding-model");
        user = userRepository.saveAndFlush(User.builder()
                .email("personality-pipeline-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hashed")
                .nickname("personality-pipeline-" + UUID.randomUUID())
                .build());
    }

    @AfterEach
    void tearDown() {
        if (user == null || !userRepository.existsById(user.getId())) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            answerRepository.deleteAllByUserId(user.getId());
            embeddingRepository.findById(user.getId()).ifPresent(embeddingRepository::delete);
            profileRepository.findById(user.getId()).ifPresent(profile -> {
                // 현재 테스트 DB가 생성된 시점에 따라 태그 FK의 ON DELETE CASCADE가
                // 없을 수 있으므로 요소 컬렉션을 먼저 비웁니다.
                profile.getStyleTags().clear();
                profileRepository.flush();
                profileRepository.delete(profile);
            });
            embeddingRepository.flush();
            profileRepository.flush();
            userRepository.deleteById(user.getId());
        });
    }

    @Test
    void savesTextOnlyEmbeddingAfterProfileSaveCommits() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        when(aiClient.embed("조용한 식사를 좋아하고 편안하게 대화하고 싶어요."))
                .thenReturn(Optional.of(vector));

        PersonalityProfileResponse response = personalityService.upsertProfile(
                user.getId(),
                request("  조용한 식사를 좋아하고 편안하게 대화하고 싶어요.  ", true)
        );

        UserPersonalityEmbedding saved = awaitEmbedding();

        assertThat(response.selfDescription()).isEqualTo("조용한 식사를 좋아하고 편안하게 대화하고 싶어요.");
        assertThat(saved.getSourceText())
                .isEqualTo("조용한 식사를 좋아하고 편안하게 대화하고 싶어요.");
        assertThat(saved.getSourceText())
                .doesNotContain("MEAL_PERSONALITY_V1", "GOOD_LISTENER", "conversationLevel");
        assertThat(saved.getSourceVersion()).isEqualTo("PERSONALITY_FREE_TEXT_V2");
        assertThat(saved.getModelName()).isEqualTo("integration-embedding-model");
        assertThat(saved.getEmbedding()).hasSize(1536);
        assertThat(saved.getEmbedding()[0]).isEqualTo(1.0f);
        verify(aiClient).embed("조용한 식사를 좋아하고 편안하게 대화하고 싶어요.");
    }

    @Test
    void withdrawalRemovesPreviouslyGeneratedEmbeddingFromMatchingData() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        when(aiClient.embed("다시 만들 소개"))
                .thenReturn(Optional.of(vector));
        personalityService.upsertProfile(user.getId(), request("다시 만들 소개", true));
        awaitEmbedding();

        personalityService.upsertProfile(user.getId(), request(null, false));

        assertThat(embeddingRepository.findById(user.getId())).isEmpty();
        assertThat(profileRepository.findById(user.getId()).orElseThrow().getSelfDescription())
                .isNull();
    }

    @Test
    void profileSaveSucceedsWhenAiEmbeddingFails() {
        when(aiClient.embed("AI 장애 테스트"))
                .thenReturn(Optional.empty());

        PersonalityProfileResponse response = personalityService.upsertProfile(
                user.getId(), request("AI 장애 테스트", true)
        );

        assertThat(response.selfDescription()).isEqualTo("AI 장애 테스트");
        assertThat(embeddingRepository.findById(user.getId())).isEmpty();
        verify(aiClient, timeout(5_000)).embed("AI 장애 테스트");
    }

    private UserPersonalityEmbedding awaitEmbedding() {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            Optional<UserPersonalityEmbedding> embedding = embeddingRepository.findById(user.getId());
            if (embedding.isPresent()) {
                return embedding.get();
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                fail("비동기 임베딩 저장을 기다리는 중 인터럽트가 발생했습니다.");
            }
        }
        fail("프로필 저장 후 5초 안에 임베딩이 저장되지 않았습니다.");
        return null;
    }

    private PersonalityProfileUpsertRequest request(String selfDescription, boolean consent) {
        return new PersonalityProfileUpsertRequest(
                PersonalityQuestionnaireVersion.MEAL_PERSONALITY_V1,
                List.of(
                        new PersonalityAnswerRequest(
                                PersonalityDimension.CONVERSATION_LEVEL,
                                PersonalityAnswerValue.MEDIUM
                        ),
                        new PersonalityAnswerRequest(
                                PersonalityDimension.MEAL_PACE,
                                PersonalityAnswerValue.MEDIUM
                        ),
                        new PersonalityAnswerRequest(
                                PersonalityDimension.PLANNING_STYLE,
                                PersonalityAnswerValue.MEDIUM
                        ),
                        new PersonalityAnswerRequest(
                                PersonalityDimension.NOVELTY_PREFERENCE,
                                PersonalityAnswerValue.MEDIUM
                        )
                ),
                Set.of(PersonalityTag.GOOD_LISTENER),
                selfDescription,
                consent
        );
    }
}
