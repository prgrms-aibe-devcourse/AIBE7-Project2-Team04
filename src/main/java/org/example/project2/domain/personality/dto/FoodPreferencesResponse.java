package org.example.project2.domain.personality.dto;

import org.example.project2.domain.user.entity.FoodCategory;

import java.util.Set;

public record FoodPreferencesResponse(Set<FoodCategory> foodCategories) {
}
