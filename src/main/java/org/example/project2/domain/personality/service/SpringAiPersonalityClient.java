package org.example.project2.domain.personality.service;

import org.example.project2.domain.personality.entity.PersonalityTag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpringAiPersonalityClient implements PersonalityAiClient {
    private static final int MAX_SUGGESTIONS = 5;
    private static final String TAG_PROMPT = """
            사용자의 식사 동행 자기소개에서 어울리는 태그를 최대 5개 고르세요.
            반드시 아래 코드만 쉼표로 구분해 출력하고, 설명이나 마크다운은 출력하지 마세요.
            허용 코드: %s
            """.formatted(Arrays.toString(PersonalityTag.values()));

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final String embeddingModelName;

    public SpringAiPersonalityClient(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            @Value("${spring.ai.google.genai.embedding.text.model:gemini-embedding-001}")
            String embeddingModelName
    ) {
        this.chatModelProvider = chatModelProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingModelName = embeddingModelName;
    }

    @Override
    public Optional<Set<PersonalityTag>> suggestTags(String selfDescription) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return Optional.empty();
        }
        try {
            String content = ChatClient.create(chatModel)
                    .prompt()
                    .system(TAG_PROMPT)
                    .user(selfDescription)
                    .call()
                    .content();
            if (content == null) {
                return Optional.of(Set.of());
            }
            Set<PersonalityTag> tags = Arrays.stream(content.toUpperCase(Locale.ROOT).split("[^A-Z_]+"))
                    .map(SpringAiPersonalityClient::parseTag)
                    .flatMap(Optional::stream)
                    .limit(MAX_SUGGESTIONS)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(PersonalityTag.class)));
            return Optional.of(Set.copyOf(tags));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<float[]> embed(String sourceText) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(embeddingModel.embed(sourceText));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String embeddingModelName() {
        return embeddingModelName;
    }

    private static Optional<PersonalityTag> parseTag(String value) {
        try {
            return Optional.of(PersonalityTag.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
