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
import org.example.project2.domain.personality.dto.PersonalityProfileResponse;
import org.example.project2.domain.personality.dto.PersonalityProfileUpsertRequest;
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
            @Valid @RequestBody PersonalityProfileUpsertRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                personalityService.upsertProfile(userId, request)
        ));
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
