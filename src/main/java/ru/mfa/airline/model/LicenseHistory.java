package ru.mfa.airline.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "license_history")
public class LicenseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LicenseHistoryStatus status;

    @Column(name = "change_date", nullable = false)
    private OffsetDateTime changeDate;

    @Column
    private String description;

    public LicenseHistory() {
    }

    public LicenseHistory(License license, User user, LicenseHistoryStatus status, OffsetDateTime changeDate,
            String description) {
        this.license = license;
        this.user = user;
        this.status = status;
        this.changeDate = changeDate;
        this.description = description;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LicenseHistoryStatus getStatus() {
        return status;
    }

    public void setStatus(LicenseHistoryStatus status) {
        this.status = status;
    }

    public OffsetDateTime getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(OffsetDateTime changeDate) {
        this.changeDate = changeDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
