package org.example.project2.domain.user.controller;

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
import org.example.project2.domain.user.dto.PreferredRegionResponse;
import org.example.project2.domain.user.dto.PreferredRegionUpdateRequest;
import org.example.project2.domain.user.service.UserLocationPreferenceService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Preferred Region", description = "구 단위 기본 활동지역·위치 서비스 동의 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/me/preferred-region")
@RequiredArgsConstructor
public class UserLocationPreferenceController {

    private final UserLocationPreferenceService userLocationPreferenceService;

    @Operation(summary = "구 단위 기본 활동지역 및 위치 서비스 동의 조회 (FR-04-06~07)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<CommonResponse<PreferredRegionResponse>> getPreferredRegion(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                userLocationPreferenceService.getPreferredRegion(userId)
        ));
    }

    @Operation(
            summary = "구 단위 기본 활동지역 및 위치 서비스 동의 설정 (FR-04-06~07)",
            description = "위치 동의 여부와 함께 구 단위 기본 활동지역을 저장하거나 수정합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "설정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<CommonResponse<PreferredRegionResponse>> updatePreferredRegion(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PreferredRegionUpdateRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                userLocationPreferenceService.updatePreferredRegion(userId, request)
        ));
    }

    @Operation(
            summary = "기본 활동지역과 위치 서비스 동의 철회 (FR-04-06)",
            description = "선호 지역과 동의 내역을 삭제 처리합니다."
    )
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 오류",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<CommonResponse<Void>> deletePreferredRegion(
            @AuthenticationPrincipal UUID userId
    ) {
        userLocationPreferenceService.deletePreferredRegion(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
