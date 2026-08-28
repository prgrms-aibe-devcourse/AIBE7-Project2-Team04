package org.example.project2.domain.user.dto;

import java.util.UUID;

public record MyProfileResponse(
        UUID userId,
        String email,
        String nickname
) {}
