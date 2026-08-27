package org.example.project2.domain.matching.controller.preference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesResponse;
import org.example.project2.domain.matching.dto.preference.MatchingPreferencesUpdateRequest;
import org.example.project2.domain.matching.exception.preference.MatchingPreferenceErrorResponse;
import org.example.project2.domain.matching.service.preference.MatchingPreferenceService;
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

@Tag(name = "Matching Preferences", description = "내 상대방 성향 선호 중요도 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/me/matching-preferences")
@RequiredArgsConstructor
public class MatchingPreferenceController {
    private static final String UPDATE_EXAMPLE = """
            {
              "preferences": [
                {"dimension":"CONVERSATION_LEVEL","importance":5,"mode":"SIMILAR"},
                {"dimension":"MEAL_PACE","importance":4,"mode":"SIMILAR"},
                {"dimension":"PLANNING_STYLE","importance":2,"mode":"COMPLEMENTARY"},
                {"dimension":"NOVELTY_PREFERENCE","importance":3,"mode":"SIMILAR"}
              ]
            }
            """;

    private final MatchingPreferenceService matchingPreferenceService;

    @Operation(
            summary = "내 상대방 성향 선호 조회",
            description = "저장된 선호가 없으면 빈 preferences 배열을 반환하며 성향 차원 점수를 적용하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<CommonResponse<MatchingPreferencesResponse>> getPreferences(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                matchingPreferenceService.getPreferences(userId)
        ));
    }

    @Operation(
            summary = "내 상대방 성향 선호 전체 갱신",
            description = "네 가지 성향 차원의 중요도와 유사·보완 선호로 기존 목록을 전체 교체합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "갱신 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "매칭 선호 입력 오류 (MATCHING_001)",
                    content = @Content(schema = @Schema(implementation = MatchingPreferenceErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<CommonResponse<MatchingPreferencesResponse>> replacePreferences(
            @AuthenticationPrincipal UUID userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = UPDATE_EXAMPLE))
            )
            @Valid @RequestBody MatchingPreferencesUpdateRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                matchingPreferenceService.replacePreferences(userId, request)
        ));
    }
}
