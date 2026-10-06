package com.example.user_service.controller;

import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.LoginResponse;
import com.example.user_service.dto.LogoutRequest;
import com.example.user_service.dto.RefreshTokenRequest;
import com.example.user_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Authentication",
        description = "Authentication and session management"
)
@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Login",
            description = "Authenticates a user and returns access and refresh tokens"
    )
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
            ) {
        return authService.login(request);
    }

    @Operation(
            summary = "Refresh access token",
            description = "Rotates the refresh token and returns new tokens"
    )
    @PostMapping("/refresh")
    public LoginResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "Logout",
            description = "Revokes the provided refresh token"
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody LogoutRequest request
            ) {
        authService.logout(request.refreshToken());
    }

    @Operation(
            summary = "Logout from all devices",
            description = "Revokes all refresh-token sessions of the current user"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(Authentication authentication) {
        String email = authentication.getName();
        authService.logoutAll(email);
    }

}
