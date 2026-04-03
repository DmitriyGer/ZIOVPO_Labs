package ru.mfa.airline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mfa.airline.model.DeviceLicense;

import java.util.Optional;

@Repository
public interface DeviceLicenseRepository extends JpaRepository<DeviceLicense, Long> {
    long countByLicenseId(Long licenseId);

    Optional<DeviceLicense> findByLicenseIdAndDeviceId(Long licenseId, Long deviceId);
}
