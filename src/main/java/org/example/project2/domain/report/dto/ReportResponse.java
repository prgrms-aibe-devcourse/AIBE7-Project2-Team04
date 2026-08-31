package org.example.project2.domain.report.dto;

import java.time.Instant;
import java.util.UUID;
import org.example.project2.domain.report.entity.ReportCategory;

public record ReportResponse(
        Long id,
        UUID reporterId,
        String reporterNickname,
        UUID reportedUserId,
        String reportedUserNickname,
        Long matchId,
        ReportCategory category,
        String categoryDescription,
        String reason,
        Instant createdAt
) {}