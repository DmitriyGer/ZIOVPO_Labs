package ru.mfa.airline.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_license", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "license_id", "device_id" })
})
public class DeviceLicense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "activation_date", nullable = false)
    private OffsetDateTime activationDate;

    public DeviceLicense() {
    }

    public DeviceLicense(License license, Device device, OffsetDateTime activationDate) {
        this.license = license;
        this.device = device;
        this.activationDate = activationDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public OffsetDateTime getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(OffsetDateTime activationDate) {
        this.activationDate = activationDate;
    }
}
