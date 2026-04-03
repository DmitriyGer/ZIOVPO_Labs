package ru.mfa.airline.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/ping")
    public Map<String, Object> ping(Authentication authentication) {
        return Map.of(
                "status", "ok",
                "time", OffsetDateTime.now().toString(),
                "username", authentication.getName());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminPing(Authentication authentication) {
        return Map.of(
                "status", "ok",
                "message", "admin access granted",
                "username", authentication.getName());
    }
}