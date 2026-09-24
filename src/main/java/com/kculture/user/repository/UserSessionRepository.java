package com.kculture.user.repository;

import com.kculture.user.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // 유효한(만료 안 된) 토큰으로 세션 조회 — 요청마다 이걸로 사용자 식별
    Optional<UserSession> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);

    // 로그아웃
    void deleteByToken(String token);
}
