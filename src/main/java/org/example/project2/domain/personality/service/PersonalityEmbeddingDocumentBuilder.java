package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Component
public class PersonalityEmbeddingDocumentBuilder {
    private static final String DOCUMENT_VERSION = "personality-document-v1";

    public PersonalityEmbeddingDocument build(UserPersonalityProfile profile) {
        String tags = profile.getStyleTags().stream()
                .map(PersonalityTag::name)
                .sorted()
                .collect(Collectors.joining(","));
        String sourceText = """
                documentVersion=%s
                questionnaireVersion=%s
                conversationLevel=%d
                mealPace=%d
                planningStyle=%d
                noveltyPreference=%d
                confirmedTags=%s
                selfDescription=%s
                """.formatted(
                DOCUMENT_VERSION,
                profile.getQuestionnaireVersion(),
                profile.getConversationLevel(),
                profile.getMealPace(),
                profile.getPlanningStyle(),
                profile.getNoveltyPreference(),
                tags,
                profile.getSelfDescription()
        );
        return new PersonalityEmbeddingDocument(sourceText, sha256(sourceText));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return DOCUMENT_VERSION + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
