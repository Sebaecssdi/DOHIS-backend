package com.dohis.app.controller;

import com.dohis.app.service.ActivityReportService;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/activity/reports")
@RequiredArgsConstructor
public class ActivityReportController {

    private final ActivityReportService activityReportService;

    /**
     * Genera y sube el informe diario global de DOHIS a la carpeta de reportes.
     * Si no mandas "date", usa el día actual (zona America/Santiago).
     *
     * Ejemplo:
     * POST /activity/reports/daily/generate?date=2025-11-25
     */
    @PostMapping("/daily/generate")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<?> generateDailyReport(
            @RequestParam(required = false) String date
    ) {
        try {
            ZoneId zoneId = ZoneId.of("America/Santiago");

            LocalDate targetDate = (date != null && !date.isBlank())
                    ? LocalDate.parse(date)
                    : LocalDate.now(zoneId);

            File driveFile = activityReportService.generateAndUploadGlobalDailyReport(
                    targetDate,
                    zoneId
            );

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "date", targetDate.toString(),
                    "driveFileId", driveFile.getId(),
                    "driveFileName", driveFile.getName(),
                    "parents", driveFile.getParents()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }
}
