package ru.mfa.airline.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mfa.airline.dto.*;
import ru.mfa.airline.exception.NotFoundException;
import ru.mfa.airline.repository.UserRepository;
import ru.mfa.airline.service.LicenseService;

@RestController
@RequestMapping("/api/licenses")
public class LicenseController {
    private final LicenseService licenseService;
    private final UserRepository userRepository;

    public LicenseController(LicenseService licenseService, UserRepository userRepository) {
        this.licenseService = licenseService;
        this.userRepository = userRepository;
    }

    // Создаёт лицензию от имени администратора.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LicenseResponse> createLicense(@Valid @RequestBody CreateLicenseRequest request,
            Authentication authentication) {
        Long adminId = resolveCurrentUserId(authentication);
        LicenseResponse response = licenseService.createLicense(request, adminId);
        return ResponseEntity.status(201).body(response);
    }

    // Активирует лицензию на устройстве текущего пользователя.
    @PostMapping("/activate")
    public ResponseEntity<TicketResponse> activateLicense(@Valid @RequestBody ActivateLicenseRequest request,
            Authentication authentication) {
        Long userId = resolveCurrentUserId(authentication);
        TicketResponse response = licenseService.activateLicense(request, userId);
        return ResponseEntity.ok(response);
    }

    // Проверяет активность лицензии по продукту и устройству.
    @PostMapping("/check")
    public ResponseEntity<TicketResponse> checkLicense(@Valid @RequestBody CheckLicenseRequest request,
            Authentication authentication) {
        Long userId = resolveCurrentUserId(authentication);
        TicketResponse response = licenseService.checkLicense(request, userId);
        return ResponseEntity.ok(response);
    }

    // Продлевает срок действия лицензии текущего пользователя.
    @PostMapping("/renew")
    public ResponseEntity<TicketResponse> renewLicense(@Valid @RequestBody RenewLicenseRequest request,
            Authentication authentication) {
        Long userId = resolveCurrentUserId(authentication);
        TicketResponse response = licenseService.renewLicense(request, userId);
        return ResponseEntity.ok(response);
    }

    // Получает id текущего пользователя из аутентификации.
    private Long resolveCurrentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
    }
}
