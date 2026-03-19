package ru.mfa.airline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.mfa.airline.model.License;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByCode(String code);

    @Query("""
            select l
            from License l
            join DeviceLicense dl on dl.license.id = l.id
            where dl.device.id = :deviceId
              and l.user.id = :userId
              and l.product.id = :productId
              and l.blocked = false
              and l.product.blocked = false
              and l.endingDate is not null
              and l.endingDate >= :now
            """)
    Optional<License> findActiveByDeviceUserAndProduct(@Param("deviceId") Long deviceId,
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("now") OffsetDateTime now);
}
