package com.example.user_service.service;

import com.example.user_service.config.JwtProperties;
import com.example.user_service.exception.InvalidRefreshTokenException;
import com.example.user_service.exception.RefreshTokenReuseException;
import com.example.user_service.model.RefreshTokenData;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> rotateRefreshTokenScript;
    private final DefaultRedisScript<String> revokeFamilyScript;

    public String createRefreshToken(
            Long userId,
            String familyId
    ) {

        String refreshToken = UUID.randomUUID().toString();

        String tokenHash = hashToken(refreshToken);

        String tokenKey = "refresh:" + tokenHash;

        String familyKey = "refresh-family:" + familyId;

        String userSessionKey = "user-sessions:" + userId;

        RefreshTokenData tokenData = new RefreshTokenData(
                userId,
                familyId,
                "ACTIVE"
        );

        try {

            String value = objectMapper.writeValueAsString(tokenData);

            redisTemplate.opsForValue().set(
                    tokenKey,
                    value,
                    Duration.ofMillis(
                            jwtProperties.getRefreshTokenExpiration()
                    )
            );

            redisTemplate.opsForSet().add(
                    familyKey,
                    tokenHash
            );

            redisTemplate.opsForSet().add(
                  userSessionKey,
                  familyId
            );

            return refreshToken;
        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Failed to serialized refresh token data",
                    exception
            );
        }

    }

    public RefreshTokenData getTokenData(
            String refreshToken
    ) {
        String key = "refresh:" + hashToken(refreshToken);

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new InvalidRefreshTokenException(
                    "Invalid refresh token"
            );
        }

        try {

            return objectMapper.readValue(
                    value,
                    RefreshTokenData.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize refresh token data",
                    exception
            );
        }
    }

    public void markAsUsed(String refreshToken) {

        String key = "refresh:" + hashToken(refreshToken);

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new InvalidRefreshTokenException(
                    "Invalid refresh token"
            );
        }

        try {

            RefreshTokenData tokenData =
                    objectMapper.readValue(
                            value,
                            RefreshTokenData.class
                    );

            RefreshTokenData updateData =
                    new RefreshTokenData(
                            tokenData.userId(),
                            tokenData.familyId(),
                            "USED"
                    );

            String updatedValue = objectMapper.writeValueAsString(updateData);

            redisTemplate.opsForValue().set(
                    key,
                    updatedValue
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to update refresh token",
                    exception
            );
        }
    }

    public void markAsUsedAtomically(String refreshToken) {

        String key = "refresh:" + hashToken(refreshToken);

        String result = redisTemplate.execute(
                rotateRefreshTokenScript,
                Collections.singletonList(key)
        );

        if ("NOT_FOUND".equals(result)) {
            throw new InvalidRefreshTokenException(
                    "Invalid refresh token"
            );
        }

        if ("USED".equals(result)) {

            RefreshTokenData tokenData =
                    getTokenData(refreshToken);

            revokeFamily(tokenData.familyId());

            throw new RefreshTokenReuseException(
                    "Refresh token reuse detected"
            );
        }

        if ("REVOKED".equals(result)) {
            throw new InvalidRefreshTokenException(
                    "Refresh token has been revoked"
            );
        }

        if (!"OK".equals(result)) {
            throw new IllegalStateException(
                    "Unexpected refresh token status"
            );
        }
    }

    public void revokeFamily(String familyId) {

        String familyKey = "refresh-family:" + familyId;

        redisTemplate.execute(
                revokeFamilyScript,
                Collections.singletonList(familyKey)
        );

    }



//    public Long getUserId(String refreshToken) {
//
//        String key = "refresh:" + hashToken(refreshToken);
//
//        String userId = redisTemplate
//                .opsForValue()
//                .get(key);
//
//        if (userId == null) {
//            throw new RuntimeException("Invalid refresh token");
//        }
//
//        return Long.valueOf(userId);
//    }

    public void revokeRefreshToken(String refreshToken) {

        String key = "refresh:" + hashToken(refreshToken);

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return;
        }

        try {
            RefreshTokenData tokenData =
                    objectMapper.readValue(
                            value,
                            RefreshTokenData.class
                    );

            if ("REVOKED".equals(tokenData.status())) {
                return;
            }

            RefreshTokenData revokedData =
                    new RefreshTokenData(
                            tokenData.userId(),
                            tokenData.familyId(),
                            "REVOKED"
                    );

            String updatedValue = objectMapper
                    .writeValueAsString(revokedData);

            Long ttl = redisTemplate.getExpire(
                    key,
                    TimeUnit.SECONDS
            );

            redisTemplate.opsForValue().set(
                    key,
                    updatedValue
            );

            if (ttl != null && ttl > 0) {
                redisTemplate.expire(
                        key,
                        ttl,
                        TimeUnit.SECONDS
                );
            }

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to revoke refresh token",
                    exception
            );
        }
    }

    private String hashToken(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    exception
            );
        }
    }

    public void revokeAllUserSessions(Long userId) {

        String userSessionKey = "user-sessions:" + userId;

        Set<String> familyIds =
                redisTemplate.opsForSet().members(userSessionKey);

        if (familyIds == null || familyIds.isEmpty()) {
            return;
        }

        for (String familyId: familyIds) {
            revokeFamily(familyId);
        }

        redisTemplate.delete(userSessionKey);
    }
}
