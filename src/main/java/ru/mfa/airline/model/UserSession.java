package ru.mfa.airline.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 512)
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
