package com.example.user_service.service;

import com.example.user_service.config.JwtProperties;
import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.LoginResponse;
import com.example.user_service.dto.RefreshTokenRequest;
import com.example.user_service.entity.User;
import com.example.user_service.exception.InvalidCredentialsException;
import com.example.user_service.exception.InvalidRefreshTokenException;
import com.example.user_service.exception.UserNotFoundException;
import com.example.user_service.model.RefreshTokenData;
import com.example.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.accept.InvalidApiVersionException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid credentials"));

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole()
        );

        String familyId = UUID.randomUUID().toString();

        String refreshToken = refreshTokenService.createRefreshToken(
                user.getId(),
                familyId

        );

        long expiresIn = jwtProperties.getAccessTokenExpiration() / 1000;

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn
        );
    }

    public LoginResponse refresh(
            RefreshTokenRequest request
    ) {

        String oldRefreshToken = request.refreshToken();

        RefreshTokenData tokenData =
                refreshTokenService.getTokenData(oldRefreshToken);

        Long userId = tokenData.userId();
        String familyId = tokenData.familyId();

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found."));

        refreshTokenService.markAsUsedAtomically(
                oldRefreshToken
        );

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole()
        );

        String newRefreshToken =
                refreshTokenService.createRefreshToken(
                        userId,
                        familyId
                );

        return new LoginResponse(
                accessToken,
                newRefreshToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration() / 1000
        );
    }

    public void logout(String refreshToken) {

        refreshTokenService.revokeRefreshToken(refreshToken);
    }

    public void logoutAll(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found."));

        refreshTokenService.revokeAllUserSessions(
                user.getId()
        );
    }
}
