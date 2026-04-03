package ru.mfa.airline.dto;

import jakarta.validation.constraints.NotBlank;

public class ActivateLicenseRequest {
    @NotBlank
    private String activationKey;

    @NotBlank
    private String deviceName;

    @NotBlank
    private String deviceMac;

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceMac() {
        return deviceMac;
    }

    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }
}
