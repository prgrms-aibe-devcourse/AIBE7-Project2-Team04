package org.example.project2.domain.review.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매칭 상대방에 대한 재만남 의향입니다.
 *
 * <p>{@link #code}는 API와 DB에서 사용하는 고정 값입니다. 한글 라벨은 별도로
 * 관리하여 클라이언트가 화면 표시를 위해 enum 선언 이름에 의존하지 않도록 합니다.</p>
 */
public enum RevisitIntention {
    DEFINITELY_AGAIN("DEFINITELY_AGAIN", "꼭 또 보고 싶어요"),
    MAYBE_AGAIN("MAYBE_AGAIN", "기회가 되면 좋아요"),
    ENOUGH_FOR_NOW("ENOUGH_FOR_NOW", "이번 만남으로 충분해요");

    private static final Map<String, RevisitIntention> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RevisitIntention::getCode, Function.identity()));

    private final String code;
    private final String label;

    RevisitIntention(String code, String label) {
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
    public static RevisitIntention fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("재만남 의향 코드는 필수입니다.");
        }

        RevisitIntention intention = BY_CODE.get(code);
        if (intention == null) {
            throw new IllegalArgumentException("지원하지 않는 재만남 의향 코드입니다.");
        }
        return intention;
    }
}
