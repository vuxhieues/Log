package com.example.demo.repositories;

import com.example.demo.entities.CurrentUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepo extends JpaRepository<CurrentUserSession, String> {
    public Optional<CurrentUserSession> findBySessionId(String sessionId);
}
