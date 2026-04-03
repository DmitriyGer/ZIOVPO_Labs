package ru.mfa.airline.dto;

import ru.mfa.airline.model.License;

import java.time.OffsetDateTime;

public class LicenseResponse {
    private Long id;
    private String code;
    private Long productId;
    private Long typeId;
    private Long ownerId;
    private Long userId;
    private OffsetDateTime firstActivationDate;
    private OffsetDateTime endingDate;
    private boolean blocked;
    private Integer deviceCount;
    private String description;

    public LicenseResponse() {
    }

    // Преобразует сущность лицензии в ответ API.
    public static LicenseResponse from(License license) {
        LicenseResponse response = new LicenseResponse();
        response.setId(license.getId());
        response.setCode(license.getCode());
        response.setProductId(license.getProduct().getId());
        response.setTypeId(license.getType().getId());
        response.setOwnerId(license.getOwner().getId());
        response.setUserId(license.getUser() == null ? null : license.getUser().getId());
        response.setFirstActivationDate(license.getFirstActivationDate());
        response.setEndingDate(license.getEndingDate());
        response.setBlocked(license.isBlocked());
        response.setDeviceCount(license.getDeviceCount());
        response.setDescription(license.getDescription());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OffsetDateTime getFirstActivationDate() {
        return firstActivationDate;
    }

    public void setFirstActivationDate(OffsetDateTime firstActivationDate) {
        this.firstActivationDate = firstActivationDate;
    }

    public OffsetDateTime getEndingDate() {
        return endingDate;
    }

    public void setEndingDate(OffsetDateTime endingDate) {
        this.endingDate = endingDate;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Integer getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(Integer deviceCount) {
        this.deviceCount = deviceCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
