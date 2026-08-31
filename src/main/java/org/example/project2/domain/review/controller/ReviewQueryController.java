package org.example.project2.domain.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.review.dto.MyReviewSummaryResponse;
import org.example.project2.domain.review.dto.PublicReviewSummaryResponse;
import org.example.project2.domain.review.exception.ReviewErrorResponse;
import org.example.project2.domain.review.service.ReviewQueryService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User Reviews", description = "받은 후기 요약 조회 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class ReviewQueryController {
    private final ReviewQueryService reviewQueryService;

    @Operation(summary = "내가 받은 후기 요약 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 요약 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "인증 사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class)))
    })
    @GetMapping("/mypage/reviews")
    public ResponseEntity<CommonResponse<MyReviewSummaryResponse>> getMyReviewSummary(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(reviewQueryService.getMyReviewSummary(userId)));
    }

    @Operation(summary = "특정 사용자의 공개 후기 요약 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 후기 요약 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음 또는 탈퇴 사용자",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class)))
    })
    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<CommonResponse<PublicReviewSummaryResponse>> getPublicReviewSummary(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(reviewQueryService.getPublicReviewSummary(userId)));
    }
}
