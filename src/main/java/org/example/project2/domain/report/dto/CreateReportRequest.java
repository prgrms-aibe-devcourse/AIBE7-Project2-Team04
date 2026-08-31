package org.example.project2.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.project2.domain.report.entity.ReportCategory;

public record CreateReportRequest(
        @NotNull(message = "신고 대상 매칭 ID는 필수입니다.")
        Long matchId,

        @NotNull(message = "신고 유형은 필수입니다.")
        ReportCategory category,

        @NotBlank(message = "구체적인 신고 사유를 입력해 주세요.")
        String reason
) {}