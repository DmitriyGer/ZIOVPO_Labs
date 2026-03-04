package ru.mfa.airline.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mfa.airline.config.JwtTokenProvider;
import ru.mfa.airline.dto.LoginRequest;
import ru.mfa.airline.dto.TokenPairResponse;
import ru.mfa.airline.model.User;
import ru.mfa.airline.model.UserSession;
import ru.mfa.airline.repository.UserRepository;

import java.time.OffsetDateTime;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionService userSessionService;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            UserSessionService userSessionService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userSessionService = userSessionService;
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return issueTokenPair(user);
    }

    @Transactional
    public TokenPairResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsername(refreshToken, JwtTokenProvider.TokenType.REFRESH);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        UserSession session = userSessionService.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new BadCredentialsException("Token does not belong to this user");
        }

        userSessionService.ensureActive(session);
        userSessionService.markRefreshed(session);

        return issueTokenPair(user);
    }

    private TokenPairResponse issueTokenPair(User user) {
        UserSession session = userSessionService.startSession(user, jwtTokenProvider.getRefreshTtl());

        String accessToken = jwtTokenProvider.generateAccessToken(user, session.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, session.getId());
        userSessionService.attachRefreshToken(session.getId(), refreshToken);

        OffsetDateTime accessExpiresAt = OffsetDateTime.now().plus(jwtTokenProvider.getAccessTtl());
        OffsetDateTime refreshExpiresAt = session.getExpiresAt();

        return new TokenPairResponse(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt, session.getId());
    }
}
