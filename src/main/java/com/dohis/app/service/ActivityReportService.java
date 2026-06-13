package com.dohis.app.service;

import com.dohis.app.model.ActivityLog;
import com.dohis.app.repository.ActivityLogRepository;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityReportService {

    private final ActivityLogRepository activityLogRepository;
    private final DriveService driveService;

    @Value("${app.drive.reportsFolderId}")
    private String reportsFolderId;

    public String buildGlobalDailyReport(LocalDate date, ZoneId zoneId) {
        ZonedDateTime startOfDay = date.atStartOfDay(zoneId);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<ActivityLog> logs = activityLogRepository
                .findByAtBetweenOrderByAtAsc(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                );

        StringBuilder sb = new StringBuilder();
        sb.append("INFORME DIARIO DE ACTIVIDAD - DOHIS\n");
        sb.append("Fecha: ").append(date).append("\n");
        sb.append("Total eventos: ").append(logs.size()).append("\n");
        sb.append("==========================================\n\n");

        if (logs.isEmpty()) {
            sb.append("No se registraron actividades en esta fecha.\n");
            return sb.toString();
        }

        Map<String, List<ActivityLog>> byUser = logs.stream()
                .collect(Collectors.groupingBy(l ->
                        l.getUserEmail() != null ? l.getUserEmail() : "desconocido"));

        for (Map.Entry<String, List<ActivityLog>> entry : byUser.entrySet()) {
            String userEmail = entry.getKey();
            List<ActivityLog> userLogs = entry.getValue();

            sb.append("==========================================\n");
            sb.append("Usuario: ").append(userEmail).append("\n");
            sb.append("Eventos para este usuario: ").append(userLogs.size()).append("\n");
            sb.append("------------------------------------------\n");

            for (ActivityLog log : userLogs) {
                ZonedDateTime zt = log.getAt().atZone(zoneId);
                sb.append("Hora: ").append(zt.toLocalTime()).append("\n");
                sb.append("Acción: ").append(log.getAction()).append("\n");
                sb.append("TargetId: ").append(log.getTargetId()).append("\n");
                if (log.getDescription() != null) {
                    sb.append("Descripción: ").append(log.getDescription()).append("\n");
                }
                sb.append("------------------------------------------\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public File generateAndUploadGlobalDailyReport(LocalDate date, ZoneId zoneId) throws Exception {
        String reportText = buildGlobalDailyReport(date, zoneId);
        String fileName = String.format("informe_dohis_%s.txt", date);

        return driveService.uploadTextFileToFolderAsOwner(
                reportsFolderId,
                fileName,
                reportText,
                "system"
        );
    }
}
