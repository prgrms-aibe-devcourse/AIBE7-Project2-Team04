package org.example.project2.domain.auth.dto;

import java.util.UUID;

public record SignUpResponse(
        UUID userId,
        String email,
        String nickname
) {
}
