package com.example.user_service.model;

public record RefreshTokenData(
        Long userId,
        String familyId,
        String status
) {
}
