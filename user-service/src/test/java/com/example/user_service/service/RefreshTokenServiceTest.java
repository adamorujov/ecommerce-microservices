package com.example.user_service.service;

import com.example.user_service.config.JwtProperties;
import com.example.user_service.exception.InvalidRefreshTokenException;
import com.example.user_service.exception.RefreshTokenReuseException;
import com.example.user_service.model.RefreshTokenData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private DefaultRedisScript<String> rotateRefreshTokenScript;

    @Mock
    private DefaultRedisScript<String> revokeFamilyScript;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {

        refreshTokenService = new RefreshTokenService(
                redisTemplate,
                jwtProperties,
                objectMapper,
                rotateRefreshTokenScript,
                revokeFamilyScript
        );
    }


    @Test
    void shouldCreateRefreshTokenSuccessfully()
            throws Exception {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(redisTemplate.opsForSet())
                .thenReturn(setOperations);

        when(objectMapper.writeValueAsString(
                any(RefreshTokenData.class)
        )).thenReturn("""
                {
                  "userId":1,
                  "familyId":"family-123",
                  "status":"ACTIVE"
                }
                """);

        when(jwtProperties.getRefreshTokenExpiration())
                .thenReturn(604800000L);

        String token =
                refreshTokenService.createRefreshToken(
                        1L,
                        "family-123"
                );

        assertNotNull(token);

        verify(valueOperations)
                .set(
                        startsWith("refresh:"),
                        anyString(),
                        eq(Duration.ofMillis(604800000L))
                );

        verify(setOperations)
                .add(
                        eq("refresh-family:family-123"),
                        anyString()
                );

        verify(setOperations)
                .add(
                        eq("user-sessions:1"),
                        eq("family-123")
                );
    }


    @Test
    void shouldGetRefreshTokenDataSuccessfully()
            throws Exception {

        String refreshToken = "refresh-token";

        String json = """
                {
                  "userId":1,
                  "familyId":"family-123",
                  "status":"ACTIVE"
                }
                """;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(anyString()))
                .thenReturn(json);

        RefreshTokenData tokenData =
                new RefreshTokenData(
                        1L,
                        "family-123",
                        "ACTIVE"
                );

        when(objectMapper.readValue(
                json,
                RefreshTokenData.class
        )).thenReturn(tokenData);

        RefreshTokenData result =
                refreshTokenService.getTokenData(
                        refreshToken
                );

        assertEquals(1L, result.userId());

        assertEquals(
                "family-123",
                result.familyId()
        );

        assertEquals(
                "ACTIVE",
                result.status()
        );
    }


    @Test
    void shouldThrowExceptionWhenRefreshTokenDoesNotExist() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(anyString()))
                .thenReturn(null);

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.getTokenData(
                        "missing-token"
                )
        );
    }


    @Test
    void shouldThrowExceptionWhenTokenDataCannotBeDeserialized()
            throws Exception {

        String refreshToken = "refresh-token";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(anyString()))
                .thenReturn("invalid-json");

        when(objectMapper.readValue(
                eq("invalid-json"),
                eq(RefreshTokenData.class)
        )).thenThrow(
                new JacksonException("Invalid JSON") {}
        );

        assertThrows(
                IllegalStateException.class,
                () -> refreshTokenService.getTokenData(
                        refreshToken
                )
        );
    }


    @Test
    void shouldMarkRefreshTokenAsUsedAtomically() {

        String refreshToken = "refresh-token";

        when(redisTemplate.execute(
                eq(rotateRefreshTokenScript),
                anyList()
        )).thenReturn("OK");

        assertDoesNotThrow(() ->
                refreshTokenService.markAsUsedAtomically(
                        refreshToken
                )
        );

        verify(redisTemplate, times(1)).execute(
                eq(rotateRefreshTokenScript),
                anyList()
        );
    }


    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenNotFound() {

        when(redisTemplate.execute(
                eq(rotateRefreshTokenScript),
                anyList()
        )).thenReturn("NOT_FOUND");

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.markAsUsedAtomically(
                        "refresh-token"
                )
        );

        verify(redisTemplate, times(1)).execute(
                eq(rotateRefreshTokenScript),
                anyList()
        );
    }


    @Test
    void shouldDetectRefreshTokenReuse()
            throws Exception {

        String refreshToken = "refresh-token";

        // 1. Token artıq USED vəziyyətindədir
        when(redisTemplate.execute(
                eq(rotateRefreshTokenScript),
                anyList()
        )).thenReturn("USED");

        // getTokenData()
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        String json = """
            {
              "userId": 1,
              "familyId": "family-123",
              "status": "USED"
            }
            """;

        when(valueOperations.get(anyString()))
                .thenReturn(json);

        RefreshTokenData tokenData =
                new RefreshTokenData(
                        1L,
                        "family-123",
                        "USED"
                );

        when(objectMapper.readValue(
                json,
                RefreshTokenData.class
        )).thenReturn(tokenData);

        // 2. Bütün family revoke olunur
        when(redisTemplate.execute(
                eq(revokeFamilyScript),
                anyList()
        )).thenReturn("OK");

        assertThrows(
                RefreshTokenReuseException.class,
                () -> refreshTokenService.markAsUsedAtomically(
                        refreshToken
                )
        );

        verify(redisTemplate).execute(
                eq(rotateRefreshTokenScript),
                anyList()
        );

        verify(redisTemplate).execute(
                eq(revokeFamilyScript),
                anyList()
        );
    }


    @Test
    void shouldRejectRevokedRefreshToken() {

        when(redisTemplate.execute(
                eq(rotateRefreshTokenScript),
                anyList()
        )).thenReturn("REVOKED");

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.markAsUsedAtomically(
                        "refresh-token"
                )
        );

        verify(redisTemplate).execute(
                eq(rotateRefreshTokenScript),
                anyList()
        );
    }
}