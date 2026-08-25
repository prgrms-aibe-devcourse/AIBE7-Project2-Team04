package org.example.project2.domain.personality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.user.entity.FoodCategory;

import java.util.Set;

public record FoodPreferencesUpdateRequest(
        @NotNull(message = "음식 카테고리 목록은 필수입니다.")
        @Size(max = 5, message = "음식 카테고리는 최대 5개까지 선택할 수 있습니다.")
        @Schema(description = "선호 음식 카테고리 전체 목록, 최대 5개")
        Set<@NotNull(message = "음식 카테고리에는 null을 포함할 수 없습니다.") FoodCategory> foodCategories
) {
    public FoodPreferencesUpdateRequest {
        foodCategories = foodCategories == null ? null : Set.copyOf(foodCategories);
    }
}
