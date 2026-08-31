package org.example.project2.domain.review.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.project2.domain.review.entity.ImpressionTag;
import org.example.project2.domain.review.entity.RevisitIntention;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsRequiredIntentionWithOrWithoutOneTag() {
        assertThat(validator.validate(new ReviewCreateRequest(
                301L,
                RevisitIntention.DEFINITELY_AGAIN,
                ImpressionTag.PUNCTUAL
        ))).isEmpty();
        assertThat(validator.validate(new ReviewCreateRequest(
                301L,
                RevisitIntention.MAYBE_AGAIN,
                null
        ))).isEmpty();
    }

    @Test
    void rejectsMissingOrInvalidRequiredFieldsWithKoreanMessages() {
        Set<String> messages = validator.validate(new ReviewCreateRequest(
                        null,
                        null,
                        null
                )).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(messages)
                .contains("매칭 ID는 필수입니다.", "재만남 의향은 필수입니다.");
        assertThat(validator.validate(new ReviewCreateRequest(
                0L,
                RevisitIntention.DEFINITELY_AGAIN,
                null
        ))).extracting(violation -> violation.getMessage())
                .contains("매칭 ID는 1 이상이어야 합니다.");
    }
}
