package org.example.project2.domain.review.controller;

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
import org.example.project2.domain.review.dto.ReviewCreateRequest;
import org.example.project2.domain.review.dto.ReviewCreateResponse;
import org.example.project2.domain.review.exception.ReviewErrorResponse;
import org.example.project2.domain.review.service.ReviewCommandService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User Reviews", description = "매칭 후기 작성 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewCommandController {
    private final ReviewCommandService reviewCommandService;

    @Operation(summary = "매칭 상대방 후기 작성")
    @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "후기 작성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "매칭 참여자 아님 또는 작성 대상 비활성",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "매칭 없음",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "작성 불가 매칭 또는 중복 작성",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "후기 작성 기간 만료",
                    content = @Content(schema = @Schema(implementation = ReviewErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CommonResponse<ReviewCreateResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CommonResponse.success(reviewCommandService.create(userId, request)));
    }
}
