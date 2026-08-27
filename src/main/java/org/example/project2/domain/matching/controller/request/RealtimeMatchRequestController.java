package org.example.project2.domain.matching.controller.request;

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
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestCreateRequest;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestResponse;
import org.example.project2.domain.matching.dto.request.RealtimeMatchRequestStatusResponse;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorResponse;
import org.example.project2.domain.matching.service.request.RealtimeMatchRequestService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Realtime Match Requests", description = "실시간 매칭 요청 생성·조회·취소 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/matches/realtime/requests")
@RequiredArgsConstructor
public class RealtimeMatchRequestController {
    private final RealtimeMatchRequestService realtimeMatchRequestService;

    @Operation(summary = "실시간 매칭 요청 생성")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 생성 및 대기 등록 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 오류 또는 위치 서비스 미동의"),
            @ApiResponse(responseCode = "409", description = "활성 요청 중복"),
            @ApiResponse(responseCode = "422", description = "요청 값 또는 선택 위치 오류",
                    content = @Content(schema = @Schema(implementation = RealtimeMatchRequestErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CommonResponse<RealtimeMatchRequestResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RealtimeMatchRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.success(
                realtimeMatchRequestService.create(userId, request)
        ));
    }

    @Operation(summary = "내 현재 실시간 매칭 요청 조회")
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<RealtimeMatchRequestStatusResponse>> getCurrent(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                realtimeMatchRequestService.getCurrent(userId)
        ));
    }

    @Operation(summary = "내 실시간 매칭 요청 취소")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @DeleteMapping("/{requestId}")
    public ResponseEntity<CommonResponse<Void>> cancel(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long requestId
    ) {
        realtimeMatchRequestService.cancel(userId, requestId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
