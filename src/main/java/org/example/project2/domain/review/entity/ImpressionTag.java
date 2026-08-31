package org.example.project2.domain.review.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매칭 상대방에 대해 선택하는 인상 태그입니다.
 *
 * <p>API에서는 단일 태그 값만 허용합니다. 리뷰 요청의 nullable 필드는 태그를
 * 선택하지 않은 경우를 나타내며, 배열 형식은 유효하지 않습니다.</p>
 */
public enum ImpressionTag {
    PUNCTUAL("PUNCTUAL", "시간 약속"),
    COMFORTABLE_CONVERSATION("COMFORTABLE_CONVERSATION", "편안한 대화"),
    CONSIDERATE("CONSIDERATE", "배려"),
    ACTIVE_PARTICIPATION("ACTIVE_PARTICIPATION", "적극적인 참여");

    private static final Map<String, ImpressionTag> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ImpressionTag::getCode, Function.identity()));

    private final String code;
    private final String label;

    ImpressionTag(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * JSON과 향후 {@code user_reviews} 컬럼에 사용하는 고정 값입니다.
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 클라이언트의 라벨 매핑이나 DTO 어댑터에서 사용하는 한글 표시 문구입니다.
     */
    public String getLabel() {
        return label;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ImpressionTag fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("인상 태그 코드는 비어 있을 수 없습니다.");
        }

        ImpressionTag tag = BY_CODE.get(code);
        if (tag == null) {
            throw new IllegalArgumentException("지원하지 않는 인상 태그 코드입니다.");
        }
        return tag;
    }
}
