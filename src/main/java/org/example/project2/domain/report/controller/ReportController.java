package org.example.project2.domain.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.report.dto.CreateReportRequest;
import org.example.project2.domain.report.service.ReportService;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User Report", description = "불량 이용자 신고 API")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @Operation(summary = "불량 이용자 신고 접수")
    @PostMapping
    public ResponseEntity<CommonResponse<Void>> report(
            @AuthenticationPrincipal UUID reporterId,
            @Validated @RequestBody CreateReportRequest request
    ) {
        reportService.report(reporterId, request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}