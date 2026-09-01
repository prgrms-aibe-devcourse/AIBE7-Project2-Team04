package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 텍스트 키워드 태그 추출 응답")
public record PersonalityKeywordExtractionResponse(
        @Schema(description = "AI 키워드 추출 기능 사용 가능 여부")
        boolean available,

        @Schema(description = "AI가 텍스트에서 추출한 핵심 키워드 태그 목록")
        List<String> keywords
) {
}
