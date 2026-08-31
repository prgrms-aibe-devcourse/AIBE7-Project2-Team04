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
 * <p>{@link #code} is the stable API/DB value. The Korean label is kept
 * separately so clients do not have to depend on enum declaration names for
 * display text.</p>
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
     * Stable value used in JSON and the future {@code user_reviews} column.
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Korean display text for a client-side label mapping or DTO adapter.
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
