package ru.mfa.airline.dto;

import java.time.OffsetDateTime;

public class Ticket {
    private OffsetDateTime serverDate;
    private Long ticketTtlSeconds;
    private OffsetDateTime activationDate;
    private OffsetDateTime expirationDate;
    private Long userId;
    private Long deviceId;
    private boolean blocked;

    public Ticket() {
    }

    public OffsetDateTime getServerDate() {
        return serverDate;
    }

    public void setServerDate(OffsetDateTime serverDate) {
        this.serverDate = serverDate;
    }

    public Long getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    public void setTicketTtlSeconds(Long ticketTtlSeconds) {
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    public OffsetDateTime getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(OffsetDateTime activationDate) {
        this.activationDate = activationDate;
    }

    public OffsetDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(OffsetDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}
