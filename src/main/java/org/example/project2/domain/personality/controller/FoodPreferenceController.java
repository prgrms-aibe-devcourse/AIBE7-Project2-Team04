package org.example.project2.domain.personality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.personality.dto.FoodPreferencesResponse;
import org.example.project2.domain.personality.dto.FoodPreferencesUpdateRequest;
import org.example.project2.domain.personality.exception.PersonalityErrorResponse;
import org.example.project2.domain.personality.service.PersonalityService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Food Preferences", description = "내 음식 카테고리 선호 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/me/food-preferences")
@RequiredArgsConstructor
public class FoodPreferenceController {
    private final PersonalityService personalityService;

    @Operation(summary = "내 음식 선호 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<CommonResponse<FoodPreferencesResponse>> getFoodPreferences(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.getFoodPreferences(userId)
        ));
    }

    @Operation(
            summary = "내 음식 선호 전체 갱신",
            description = "요청에 포함된 최대 5개의 음식 카테고리로 기존 목록을 전체 교체합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "갱신 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "음식 카테고리 입력 오류 (PERSONALITY_002)",
                    content = @Content(schema = @Schema(implementation = PersonalityErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<CommonResponse<FoodPreferencesResponse>> updateFoodPreferences(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody FoodPreferencesUpdateRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.updateFoodPreferences(userId, request)
        ));
    }
}
