package ru.mfa.airline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mfa.airline.model.SessionStatus;
import ru.mfa.airline.model.UserSession;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshToken(String refreshToken);

    long countByUserIdAndStatus(Long userId, SessionStatus status);
}
