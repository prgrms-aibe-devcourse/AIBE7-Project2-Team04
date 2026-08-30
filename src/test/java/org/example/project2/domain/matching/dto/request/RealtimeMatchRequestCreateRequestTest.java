package org.example.project2.domain.matching.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.FoodCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
        RealtimeMatchRequestCreateRequest normalized = requestWithText(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                "  대화를 편하게 이어가는 분  "
        );
        RealtimeMatchRequestCreateRequest overLimit = requestWithText(
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

    @Test
    void normalizesBlankDesiredPersonalityTextToNull() {
        RealtimeMatchRequestCreateRequest request = requestWithText(
                Set.of(
                        PersonalityTag.GOOD_LISTENER,
                        PersonalityTag.FOOD_TALK,
                        PersonalityTag.ENJOY_DESSERT
                ),
                "   "
        );

        assertThat(request.desiredPersonalityText()).isNull();
    }

    @Test
    void doesNotCollectDietaryRestrictionFields() {
        Set<String> fieldNames = Arrays.stream(RealtimeMatchRequestCreateRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fieldNames).contains("foodCategory");
        assertThat(fieldNames).doesNotContain(
                "allergy", "allergies", "dietaryRestriction", "dietaryRestrictions",
                "vegetarian", "vegan", "religiousRestriction", "healthCondition"
        );
    }

    private RealtimeMatchRequestCreateRequest request(PersonalityTag... tags) {
        return requestWithText(Set.of(tags), "  대화를 편하게 이어가는 분  ");
    }

    private RealtimeMatchRequestCreateRequest requestWithText(
            Set<PersonalityTag> tags,
            String desiredPersonalityText
    ) {
        return new RealtimeMatchRequestCreateRequest(
                FoodCategory.KOREAN,
                Instant.now().plusSeconds(3_600),
                "11680",
                "사용자 입력 표시명",
                "강남역",
                37.501,
                127.039,
                null,
                tags,
                desiredPersonalityText
        );
    }
}
