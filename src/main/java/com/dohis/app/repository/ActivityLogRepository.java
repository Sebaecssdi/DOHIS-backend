package com.dohis.app.repository;

import com.dohis.app.model.ActivityLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {

    // Ya lo tenías para informes por usuario
    List<ActivityLog> findByUserEmailAndAtBetweenOrderByAtAsc(
            String userEmail,
            Instant start,
            Instant end
    );

    // ⬇️ NUEVO: todos los logs del día, cualquier usuario
    List<ActivityLog> findByAtBetweenOrderByAtAsc(
            Instant start,
            Instant end
    );
}
