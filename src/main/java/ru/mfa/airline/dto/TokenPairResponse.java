package ru.mfa.airline.dto;

import java.time.OffsetDateTime;

public class TokenPairResponse {
    private String accessToken;
    private String refreshToken;
    private OffsetDateTime accessExpiresAt;
    private OffsetDateTime refreshExpiresAt;
    private Long sessionId;

    public TokenPairResponse() {
    }

    public TokenPairResponse(String accessToken, String refreshToken, OffsetDateTime accessExpiresAt,
            OffsetDateTime refreshExpiresAt, Long sessionId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.sessionId = sessionId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public OffsetDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(OffsetDateTime accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public OffsetDateTime getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public void setRefreshExpiresAt(OffsetDateTime refreshExpiresAt) {
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
