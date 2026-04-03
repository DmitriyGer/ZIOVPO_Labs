package ru.mfa.airline.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mfa.airline.dto.LoginRequest;
import ru.mfa.airline.dto.RefreshRequest;
import ru.mfa.airline.dto.RegisterRequest;
import ru.mfa.airline.dto.TokenPairResponse;
import ru.mfa.airline.dto.UserResponse;
import ru.mfa.airline.model.User;
import ru.mfa.airline.service.AuthService;
import ru.mfa.airline.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        validateUsername(request.getUsername());
        validatePassword(request.getPassword());

        String role = normalizeRole(request.getRole());
        User user = userService.createUser(request.getUsername(), request.getPassword(), role);

        return ResponseEntity.status(201).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPairResponse tokenPair = authService.login(request);
        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPairResponse tokenPair = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(tokenPair);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean hasSpecialChar = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
        if (!hasSpecialChar) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }

        boolean hasDigit = password.matches(".*\\d.*");
        if (!hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }

        boolean hasUpperCase = password.matches(".*[A-Z].*");
        if (!hasUpperCase) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }

        boolean hasLowerCase = password.matches(".*[a-z].*");
        if (!hasLowerCase) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }

        if (!username.matches("^[A-Za-z0-9_.-]{3,50}$")) {
            throw new IllegalArgumentException(
                    "Username must be 3-50 characters and contain only letters, numbers, dot, dash or underscore");
        }
    }

    private String normalizeRole(String incomingRole) {
        String role = (incomingRole == null || incomingRole.isBlank()) ? "USER" : incomingRole.trim();
        role = role.toUpperCase();

        if (!role.equals("USER") && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }

        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return role;
    }
}
