package com.dohis.app.service;

import com.dohis.app.model.ActivityLog;
import com.dohis.app.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    /**
     * Método genérico: loguea usando directamente el email
     */
    public void log(String userEmail, String action, String targetId, String description) {
        ActivityLog log = ActivityLog.builder()
                .userEmail(userEmail)
                .action(action)
                .targetId(targetId)
                .description(description)
                .at(Instant.now())
                .build();

        activityLogRepository.save(log);
    }

    /**
     * Método cómodo para usar desde los controllers con Authentication
     * (asumiendo que authentication.getName() es el email del usuario).
     */
    public void log(Authentication authentication, String action, String targetId, String description) {
        String email = (authentication != null) ? authentication.getName() : "desconocido";
        log(email, action, targetId, description);
    }
}
