package ru.mfa.airline.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mfa.airline.dto.*;
import ru.mfa.airline.exception.ConflictException;
import ru.mfa.airline.exception.ForbiddenException;
import ru.mfa.airline.exception.NotFoundException;
import ru.mfa.airline.model.*;
import ru.mfa.airline.repository.*;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class LicenseService {
    private final ProductRepository productRepository;
    private final LicenseTypeRepository licenseTypeRepository;
    private final LicenseRepository licenseRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final TicketService ticketService;

    public LicenseService(ProductRepository productRepository,
            LicenseTypeRepository licenseTypeRepository,
            LicenseRepository licenseRepository,
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DeviceLicenseRepository deviceLicenseRepository,
            LicenseHistoryRepository licenseHistoryRepository,
            TicketService ticketService) {
        this.productRepository = productRepository;
        this.licenseTypeRepository = licenseTypeRepository;
        this.licenseRepository = licenseRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.deviceLicenseRepository = deviceLicenseRepository;
        this.licenseHistoryRepository = licenseHistoryRepository;
        this.ticketService = ticketService;
    }

    // Создаёт новую лицензию и записывает событие в историю.
    @Transactional
    public LicenseResponse createLicense(CreateLicenseRequest request, Long adminId) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
        LicenseType type = licenseTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new NotFoundException("License type not found"));
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin not found"));
        if (!isUserActive(owner)) {
            throw new NotFoundException("Owner is not active");
        }

        License license = new License();
        license.setCode(generateCode());
        license.setProduct(product);
        license.setType(type);
        license.setOwner(owner);
        license.setBlocked(false);
        license.setDeviceCount(request.getDeviceCount());
        license.setDescription(request.getDescription());

        License saved = licenseRepository.save(license);
        writeHistory(saved, admin, LicenseHistoryStatus.CREATED, "License created");
        return LicenseResponse.from(saved);
    }

    // Активирует лицензию на устройстве пользователя и возвращает подписанный Ticket.
    @Transactional
    public TicketResponse activateLicense(ActivateLicenseRequest request, Long userId) {
        User user = findUserOrFail(userId);
        License license = findLicenseByCodeOrFail(request.getActivationKey());
        ensureLicenseIsUsable(license);

        if (license.getUser() != null && !license.getUser().getId().equals(userId)) {
            throw new ForbiddenException("License already belongs to another user");
        }

        Device device = resolveDeviceForUser(request.getDeviceMac(), request.getDeviceName(), user);
        OffsetDateTime now = OffsetDateTime.now();

        if (license.getUser() == null) {
            license.setUser(user);
            license.setFirstActivationDate(now);
            license.setEndingDate(now.plusDays(license.getType().getDefaultDurationInDays()));
            licenseRepository.save(license);
            bindDeviceToLicense(license, device, now);
            writeHistory(license, user, LicenseHistoryStatus.ACTIVATED, "License activated");
            return ticketService.buildSignedResponse(license, device);
        }

        if (license.getEndingDate() == null || license.getEndingDate().isBefore(now)) {
            throw new ConflictException("License is expired");
        }

        boolean alreadyBound = deviceLicenseRepository.findByLicenseIdAndDeviceId(license.getId(), device.getId()).isPresent();
        if (!alreadyBound) {
            long currentCount = deviceLicenseRepository.countByLicenseId(license.getId());
            if (currentCount >= license.getDeviceCount()) {
                throw new ConflictException("Device limit reached");
            }
            bindDeviceToLicense(license, device, now);
            writeHistory(license, user, LicenseHistoryStatus.ACTIVATED, "License activated on extra device");
        }

        return ticketService.buildSignedResponse(license, device);
    }

    // Проверяет активную лицензию по устройству и продукту.
    @Transactional(readOnly = true)
    public TicketResponse checkLicense(CheckLicenseRequest request, Long userId) {
        Device device = deviceRepository.findByMacAddress(normalizeMac(request.getDeviceMac()))
                .orElseThrow(() -> new NotFoundException("Device not found"));
        if (!device.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Device belongs to another user");
        }

        License license = licenseRepository.findActiveByDeviceUserAndProduct(
                device.getId(),
                userId,
                request.getProductId(),
                OffsetDateTime.now()).orElseThrow(() -> new NotFoundException("Active license not found"));

        return ticketService.buildSignedResponse(license, device);
    }

    // Продлевает срок действия лицензии и возвращает новый Ticket.
    @Transactional
    public TicketResponse renewLicense(RenewLicenseRequest request, Long userId) {
        User user = findUserOrFail(userId);
        License license = findLicenseByCodeOrFail(request.getActivationKey());
        ensureLicenseIsUsable(license);

        if (license.getUser() == null) {
            throw new ConflictException("License is not activated");
        }
        if (!license.getUser().getId().equals(userId)) {
            throw new ForbiddenException("License belongs to another user");
        }
        if (license.getEndingDate() == null) {
            throw new ConflictException("License has no expiration date");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (license.getEndingDate().isAfter(now.plusDays(7))) {
            throw new ConflictException("Renew is allowed only for expired licenses or in last 7 days");
        }

        OffsetDateTime baseDate = license.getEndingDate().isAfter(now) ? license.getEndingDate() : now;
        license.setEndingDate(baseDate.plusDays(license.getType().getDefaultDurationInDays()));
        licenseRepository.save(license);
        writeHistory(license, user, LicenseHistoryStatus.RENEWED, "License renewed");

        Device device = deviceRepository.findByMacAddress(normalizeMac(request.getDeviceMac()))
                .orElseThrow(() -> new NotFoundException("Device not found"));
        if (!device.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Device belongs to another user");
        }
        boolean bound = deviceLicenseRepository.findByLicenseIdAndDeviceId(license.getId(), device.getId()).isPresent();
        if (!bound) {
            throw new ConflictException("Device is not activated for this license");
        }

        return ticketService.buildSignedResponse(license, device);
    }

    // Находит пользователя по id или выбрасывает 404.
    private User findUserOrFail(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    // Находит лицензию по коду или выбрасывает 404.
    private License findLicenseByCodeOrFail(String code) {
        return licenseRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("License not found"));
    }

    // Проверяет, что лицензия и продукт не заблокированы.
    private void ensureLicenseIsUsable(License license) {
        if (license.isBlocked() || license.getProduct().isBlocked()) {
            throw new ConflictException("License or product is blocked");
        }
    }

    // Ищет или создаёт устройство для текущего пользователя.
    private Device resolveDeviceForUser(String macAddress, String deviceName, User user) {
        String normalizedMac = normalizeMac(macAddress);
        return deviceRepository.findByMacAddress(normalizedMac)
                .map(existing -> {
                    if (!existing.getUser().getId().equals(user.getId())) {
                        throw new ForbiddenException("Device belongs to another user");
                    }
                    if (deviceName != null && !deviceName.isBlank()) {
                        existing.setName(deviceName.trim());
                        return deviceRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> deviceRepository.save(new Device(deviceName.trim(), normalizedMac, user)));
    }

    // Создаёт связь между лицензией и устройством.
    private void bindDeviceToLicense(License license, Device device, OffsetDateTime activationDate) {
        deviceLicenseRepository.findByLicenseIdAndDeviceId(license.getId(), device.getId())
                .orElseGet(() -> deviceLicenseRepository.save(new DeviceLicense(license, device, activationDate)));
    }

    // Пишет бизнес-событие по лицензии в журнал.
    private void writeHistory(License license, User actor, LicenseHistoryStatus status, String description) {
        LicenseHistory event = new LicenseHistory(license, actor, status, OffsetDateTime.now(), description);
        licenseHistoryRepository.save(event);
    }

    // Нормализует MAC-адрес для единого хранения.
    private String normalizeMac(String macAddress) {
        return macAddress.trim().toUpperCase(Locale.ROOT);
    }

    // Проверяет, что учётная запись пользователя активна.
    private boolean isUserActive(User user) {
        return !Boolean.TRUE.equals(user.getDisabled())
                && !Boolean.TRUE.equals(user.getAccountLocked())
                && !Boolean.TRUE.equals(user.getAccountExpired())
                && !Boolean.TRUE.equals(user.getCredentialsExpired());
    }

    // Генерирует уникальный код активации лицензии.
    private String generateCode() {
        String code;
        do {
            code = "LIC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        } while (licenseRepository.findByCode(code).isPresent());
        return code;
    }
}
