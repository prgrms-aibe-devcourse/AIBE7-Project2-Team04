package org.example.project2.domain.matching.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeMatchRequestCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsThreeToFiveDesiredPersonalityTags() {
        assertThat(validator.validate(request(
                PersonalityTag.GOOD_LISTENER,
                PersonalityTag.FOOD_TALK,
                PersonalityTag.ENJOY_DESSERT
        ))).isEmpty();

        assertThat(validator.validate(request(
                PersonalityTag.GOOD_LISTENER,
                PersonalityTag.FOOD_TALK,
                PersonalityTag.ENJOY_DESSERT,
                PersonalityTag.CALM_ATMOSPHERE,
                PersonalityTag.SHARE_DISHES
        ))).isEmpty();
    }

    @Test
    void rejectsFewerThanThreeOrMoreThanFiveDesiredPersonalityTags() {
        assertThat(validator.validate(request(
                PersonalityTag.GOOD_LISTENER,
                PersonalityTag.FOOD_TALK
        ))).extracting(violation -> violation.getMessage())
                .containsExactly("원하는 상대 성향 태그는 3개 이상 5개 이하로 선택할 수 있습니다.");

        assertThat(validator.validate(request(
                PersonalityTag.GOOD_LISTENER,
                PersonalityTag.FOOD_TALK,
                PersonalityTag.ENJOY_DESSERT,
                PersonalityTag.CALM_ATMOSPHERE,
                PersonalityTag.SHARE_DISHES,
                PersonalityTag.DEEP_TALK
        ))).extracting(violation -> violation.getMessage())
                .containsExactly("원하는 상대 성향 태그는 3개 이상 5개 이하로 선택할 수 있습니다.");
    }

    @Test
    void normalizesOptionalDesiredPersonalityTextAndRejectsTextOver300Characters() {
        RealtimeMatchRequestCreateRequest normalized = new RealtimeMatchRequestCreateRequest(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                "  대화를 편하게 이어가는 분  "
        );
        RealtimeMatchRequestCreateRequest overLimit = new RealtimeMatchRequestCreateRequest(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                "가".repeat(301)
        );

        assertThat(normalized.desiredPersonalityText()).isEqualTo("대화를 편하게 이어가는 분");
        assertThat(validator.validate(overLimit)).extracting(violation -> violation.getMessage())
                .containsExactly("원하는 상대 성향 설명은 최대 300자까지 입력할 수 있습니다.");
    }

    private RealtimeMatchRequestCreateRequest request(PersonalityTag... tags) {
        return new RealtimeMatchRequestCreateRequest(Set.of(tags), "  대화를 편하게 이어가는 분  ");
    }
}
