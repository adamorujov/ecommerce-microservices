package com.example.user_service.service;

import com.example.user_service.config.JwtProperties;
import com.example.user_service.dto.ChangePasswordRequest;
import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.LoginResponse;
import com.example.user_service.dto.RefreshTokenRequest;
import com.example.user_service.entity.User;
import com.example.user_service.exception.*;
import com.example.user_service.model.RefreshTokenData;
import com.example.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginSuccessfully() {

        LoginRequest request =
                new LoginRequest(
                        "test@gmail.com",
                        "Password123"
                );

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "Password123",
                "hashed-password"
        )).thenReturn(true);

        when(jwtService.generateAccessToken(
                "test@gmail.com",
                "CUSTOMER"
        )).thenReturn("access-token");

        when(refreshTokenService.createRefreshToken(
                eq(1L),
                anyString()
        )).thenReturn("refresh-token");

        when(jwtProperties.getAccessTokenExpiration())
                .thenReturn(900000L);

        LoginResponse result =
                authService.login(request);

        assertNotNull(result);

        assertEquals(
                "access-token",
                result.accessToken()
        );

        assertEquals(
                "refresh-token",
                result.refreshToken()
        );

        assertEquals(
                "Bearer",
                result.tokenType()
        );

        assertEquals(
                900,
                result.expiresIn()
        );

        verify(userRepository)
                .findByEmail("test@gmail.com");

        verify(passwordEncoder)
                .matches(
                        "Password123",
                        "hashed-password"
                );

        verify(jwtService)
                .generateAccessToken(
                        "test@gmail.com",
                        "CUSTOMER"
                );

        verify(refreshTokenService)
                .createRefreshToken(
                        eq(1L),
                        anyString()
                );
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsIncorrect() {

        LoginRequest request =
                new LoginRequest(
                        "test@gmail.com",
                        "WrongPassword"
                );

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongPassword",
                "hashed-password"
        )).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(passwordEncoder)
                .matches(
                        "WrongPassword",
                        "hashed-password"
                );

        verifyNoInteractions(
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void shouldThrowInvalidCredentialsWhenUserDoesNotExist() {

        LoginRequest request =
                new LoginRequest(
                        "unknown@gmail.com",
                        "Password123"
                );

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(userRepository)
                .findByEmail("unknown@gmail.com");

        verifyNoInteractions(
                passwordEncoder,
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void shouldRefreshTokenSuccessfully() {

        String oldRefreshToken = "old-refresh-token";

        RefreshTokenData tokenData =
                new RefreshTokenData(
                        1L,
                        "family-123",
                        "ACTIVE"
                );

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        when(refreshTokenService.getTokenData(oldRefreshToken))
                .thenReturn(tokenData);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(jwtService.generateAccessToken(
                "test@gmail.com",
                "CUSTOMER"
        )).thenReturn("new-access-token");

        when(refreshTokenService.createRefreshToken(
                1L,
                "family-123"
        )).thenReturn("new-refresh-token");

        when(jwtProperties.getAccessTokenExpiration())
                .thenReturn(900000L);

        LoginResponse result =
                authService.refresh(
                        new RefreshTokenRequest(oldRefreshToken)
                );

        assertNotNull(result);

        assertEquals(
                "new-access-token",
                result.accessToken()
        );

        assertEquals(
                "new-refresh-token",
                result.refreshToken()
        );

        assertEquals(
                "Bearer",
                result.tokenType()
        );

        assertEquals(
                900,
                result.expiresIn()
        );

        verify(refreshTokenService)
                .getTokenData(oldRefreshToken);

        verify(userRepository)
                .findById(1L);

        verify(jwtService)
                .generateAccessToken(
                        "test@gmail.com",
                        "CUSTOMER"
                );

        verify(refreshTokenService)
                .markAsUsedAtomically(oldRefreshToken);

        verify(refreshTokenService)
                .createRefreshToken(
                        1L,
                        "family-123"
                );
    }

    @Test
    void shouldRejectInvalidRefreshToken() {

        String refreshToken = "invalid-token";

        when(refreshTokenService.getTokenData(refreshToken))
                .thenThrow(
                        new InvalidRefreshTokenException(
                                "Invalid refresh token"
                        )
                );

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(
                        new RefreshTokenRequest(refreshToken)
                )
        );

        verify(refreshTokenService)
                .getTokenData(refreshToken);

        verifyNoInteractions(
                userRepository,
                jwtService
        );
    }

    @Test
    void shouldThrowUserNotFoundWhenRefreshingForMissingUser() {

        String refreshToken = "refresh-token";

        RefreshTokenData tokenData =
                new RefreshTokenData(
                        1L,
                        "family-123",
                        "ACTIVE"
                );

        when(refreshTokenService.getTokenData(refreshToken))
                .thenReturn(tokenData);

        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> authService.refresh(
                        new RefreshTokenRequest(refreshToken)
                )
        );

        verify(refreshTokenService)
                .getTokenData(refreshToken);

        verify(userRepository)
                .findById(1L);

        verifyNoInteractions(jwtService);

        verify(refreshTokenService, never())
                .markAsUsedAtomically(anyString());
    }

    @Test
    void shouldDetectRefreshTokenReuse() {

        String oldRefreshToken = "already-used-token";

        RefreshTokenData tokenData =
                new RefreshTokenData(
                        1L,
                        "family-123",
                        "USED"
                );

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        when(refreshTokenService.getTokenData(oldRefreshToken))
                .thenReturn(tokenData);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        doThrow(
                new RefreshTokenReuseException(
                        "Refresh token reuse detected"
                )
        ).when(refreshTokenService)
                .markAsUsedAtomically(oldRefreshToken);

        assertThrows(
                RefreshTokenReuseException.class,
                () -> authService.refresh(
                        new RefreshTokenRequest(oldRefreshToken)
                )
        );

        verify(refreshTokenService)
                .markAsUsedAtomically(oldRefreshToken);

        verify(refreshTokenService, never())
                .createRefreshToken(
                        anyLong(),
                        anyString()
                );

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldLogoutSuccessfully() {

        String refreshToken = "refresh-token";

        authService.logout(refreshToken);

        verify(refreshTokenService)
                .revokeRefreshToken(refreshToken);
    }

    @Test
    void shouldLogoutAllUserSessions() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .build();

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        authService.logoutAll("test@gmail.com");

        verify(userRepository)
                .findByEmail("test@gmail.com");

        verify(refreshTokenService)
                .revokeAllUserSessions(1L);
    }

    @Test
    void shouldThrowUserNotFoundWhenLogoutAllUserDoesNotExist() {

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> authService.logoutAll(
                        "unknown@gmail.com"
                )
        );

        verify(refreshTokenService, never())
                .revokeAllUserSessions(anyLong());
    }

}
