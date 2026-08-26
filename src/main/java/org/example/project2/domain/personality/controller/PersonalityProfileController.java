package org.example.project2.domain.personality.controller;

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
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionRequest;
import org.example.project2.domain.personality.dto.PersonalityTagSuggestionResponse;
import org.example.project2.domain.personality.exception.PersonalityErrorResponse;
import org.example.project2.domain.personality.service.PersonalityService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Personality", description = "내 식사 스타일 온보딩 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/me/personality-profile")
@RequiredArgsConstructor
public class PersonalityProfileController {
    private static final String PROFILE_UPSERT_EXAMPLE = """
            {
              "questionnaireVersion": "MEAL_PERSONALITY_V1",
              "answers": [
                {"questionCode": "CONVERSATION_LEVEL", "value": 1},
                {"questionCode": "MEAL_PACE", "value": 3},
                {"questionCode": "PLANNING_STYLE", "value": 5},
                {"questionCode": "NOVELTY_PREFERENCE", "value": 3}
              ],
              "styleTags": ["GOOD_LISTENER"],
              "selfDescription": "대화도 좋지만 식사에 집중하는 조용한 자리를 좋아해요.",
              "aiAnalysisConsent": true
            }
            """;

    private final PersonalityService personalityService;

    @Operation(
            summary = "내 성향 프로필 조회",
            description = "프로필이 없어도 200과 completed=false를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<CommonResponse<PersonalityProfileResponse>> getProfile(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.getProfile(userId)
        ));
    }

    @Operation(
            summary = "내 성향 프로필 제출",
            description = "네 가지 카드 응답과 태그를 최초 저장하거나 전체 교체합니다. 인증 쿠키와 CSRF 토큰이 필요합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "성향 입력값 오류 (PERSONALITY_002)",
                    content = @Content(schema = @Schema(implementation = PersonalityErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<CommonResponse<PersonalityProfileResponse>> upsertProfile(
            @AuthenticationPrincipal UUID userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = PROFILE_UPSERT_EXAMPLE)
                    )
            )
            @Valid @RequestBody PersonalityProfileUpsertRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.upsertProfile(userId, request)
        ));
    }

    @Operation(
            summary = "자기소개 기반 성향 태그 추천",
            description = "AI 분석 동의가 있는 자기소개에서 태그를 최대 5개 제안합니다. 제안은 프로필에 자동 저장되지 않습니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 요청 처리 성공. AI를 사용할 수 없으면 available=false"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "동의 또는 자기소개 검증 실패")
    })
    @PostMapping("/tag-suggestions")
    public ResponseEntity<CommonResponse<PersonalityTagSuggestionResponse>> suggestTags(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PersonalityTagSuggestionRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(personalityService.suggestTags(request)));
    }

    @Operation(
            summary = "내 성향 프로필 초기화",
            description = "프로필, 원본 응답, 태그와 파생 데이터를 삭제하고 온보딩 상태를 NOT_STARTED로 변경합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초기화 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<CommonResponse<Void>> resetProfile(
            @AuthenticationPrincipal UUID userId
    ) {
        personalityService.resetProfile(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(
            summary = "성향 온보딩 건너뛰기",
            description = "프로필이 없는 사용자의 온보딩 상태를 SKIPPED로 저장합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "건너뛰기 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @PostMapping("/skip")
    public ResponseEntity<CommonResponse<PersonalityProfileResponse>> skipProfile(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.skipProfile(userId)
        ));
    }
}
