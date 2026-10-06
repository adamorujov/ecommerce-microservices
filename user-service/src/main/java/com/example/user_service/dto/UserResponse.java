package com.example.user_service.dto;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
