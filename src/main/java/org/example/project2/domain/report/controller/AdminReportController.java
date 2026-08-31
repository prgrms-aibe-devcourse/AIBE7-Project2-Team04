package org.example.project2.domain.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.dto.ChatMessageDTO;
import org.example.project2.domain.report.dto.ReportResponse;
import org.example.project2.domain.report.service.ReportService;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin Report Management", description = "관리자용 사용자 신고 및 제재 제어 API")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {
    private final ReportService reportService;

    @Operation(summary = "전체 신고 목록 조회 (최신 등록 순)")
    @GetMapping("/reports")
    public ResponseEntity<CommonResponse<List<ReportResponse>>> getAllReports() {
        List<ReportResponse> reports = reportService.getAllReports();
        return ResponseEntity.ok(CommonResponse.success(reports));
    }

    @Operation(summary = "신고된 매칭의 대화 내역 전체 확인")
    @GetMapping("/reports/{reportId}/chat-messages")
    public ResponseEntity<CommonResponse<List<ChatMessageDTO>>> getChatHistory(
            @PathVariable Long reportId
    ) {
        List<ChatMessageDTO> chatHistory = reportService.getChatHistory(reportId);
        return ResponseEntity.ok(CommonResponse.success(chatHistory));
    }

    @Operation(summary = "신고 기각 처리")
    @PostMapping("/reports/{reportId}/dismiss")
    public ResponseEntity<CommonResponse<Void>> dismissReport(@PathVariable Long reportId) {
        reportService.dismissReport(reportId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "신고 대상 경고 또는 정지 처리")
    @PostMapping("/reports/{reportId}/handle/{action}")
    public ResponseEntity<CommonResponse<Void>> handleReport(
            @PathVariable Long reportId,
            @PathVariable String action
    ) {
        reportService.handleReport(reportId, action);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "불량 회원 영구 정지(BAN)")
    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<CommonResponse<Void>> banUser(
            @PathVariable UUID userId
    ) {
        reportService.banUser(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "불량 회원 경고 조치 (3회 누적 시 정지)")
    @PostMapping("/users/{userId}/warn")
    public ResponseEntity<CommonResponse<Void>> warnUser(
            @PathVariable UUID userId
    ) {
        reportService.warnUser(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
