package ru.mfa.airline.dto;

import jakarta.validation.constraints.NotBlank;

public class RenewLicenseRequest {
    @NotBlank
    private String activationKey;

    @NotBlank
    private String deviceMac;

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getDeviceMac() {
        return deviceMac;
    }

    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }
}
