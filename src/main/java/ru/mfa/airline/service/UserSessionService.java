package ru.mfa.airline.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mfa.airline.model.SessionStatus;
import ru.mfa.airline.model.User;
import ru.mfa.airline.model.UserSession;
import ru.mfa.airline.repository.UserSessionRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    public UserSessionService(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional
    public UserSession startSession(User user, Duration refreshTtl) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setStatus(SessionStatus.ACTIVE);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plus(refreshTtl));
        session.setRefreshToken(UUID.randomUUID().toString());
        return userSessionRepository.save(session);
    }

    @Transactional
    public UserSession attachRefreshToken(Long sessionId, String refreshToken) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));
        session.setRefreshToken(refreshToken);
        session.setUpdatedAt(OffsetDateTime.now());
        return userSessionRepository.save(session);
    }

    public Optional<UserSession> findByRefreshToken(String refreshToken) {
        return userSessionRepository.findByRefreshToken(refreshToken);
    }

    @Transactional
    public void ensureActive(UserSession session) {
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setUpdatedAt(OffsetDateTime.now());
            userSessionRepository.save(session);
            throw new BadCredentialsException("Session expired");
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BadCredentialsException("Session is not active");
        }
    }

    @Transactional
    public void ensureActive(Long sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadCredentialsException("Session not found"));
        ensureActive(session);
    }

    @Transactional
    public UserSession markRefreshed(UserSession session) {
        session.setStatus(SessionStatus.REFRESHED);
        session.setUpdatedAt(OffsetDateTime.now());
        return userSessionRepository.save(session);
    }

    @Transactional
    public UserSession revokeSession(UserSession session) {
        session.setStatus(SessionStatus.REVOKED);
        session.setRevokedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return userSessionRepository.save(session);
    }
}
