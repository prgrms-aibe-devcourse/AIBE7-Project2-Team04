package org.example.project2.domain.personality.service.embedding;

import org.example.project2.domain.matching.dto.scoring.PersonalityEmbeddingVector;
import org.springframework.stereotype.Component;

@Component
public class PersonalityTextEmbeddingDocumentBuilder {
    public static final String DOCUMENT_VERSION = PersonalityEmbeddingVector.CURRENT_SOURCE_VERSION_FAMILY;

    /**
     * 자유 텍스트만 임베딩하기 위한 공통 문서를 만듭니다.
     * 카드 점수와 태그는 별도 매칭 신호이므로 문서에 섞지 않습니다.
     */
    public PersonalityEmbeddingDocument build(String text) {
        String sourceText = normalize(text);
        if (sourceText == null) {
            throw new IllegalArgumentException("임베딩할 성향 자유 텍스트는 비어 있을 수 없습니다.");
        }
        return new PersonalityEmbeddingDocument(sourceText, DOCUMENT_VERSION);
    }

    private String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
