package org.example.project2.domain.matching.dto.history;

import java.util.List;

public record MatchHistoryPageResponse(
        List<MatchHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
