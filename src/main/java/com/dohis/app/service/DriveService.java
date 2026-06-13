package com.dohis.app.service;

import com.dohis.app.model.ActivityLog;
import com.dohis.app.repository.ActivityLogRepository;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.google.api.client.http.ByteArrayContent;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

@Service
public class DriveService {

    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final String clientId;
    private final String clientSecret;
    private final String ownerRefreshToken;
    private final String rootFolderId;
    private final String appName;
    private final ActivityLogRepository logs;
    private final ActivityLogRepository activityLogRepository;

    public DriveService(
            @Value("${app.google.clientId}") String clientId,
            @Value("${app.google.clientSecret}") String clientSecret,
            @Value("${app.google.owner.refreshToken}") String ownerRefreshToken,
            @Value("${app.drive.rootFolderId}") String rootFolderId,
            @Value("${spring.application.name:DOHIS}") String appName,
            ActivityLogRepository logs,
            ActivityLogRepository activityLogRepository) {
        this.clientId = Objects.requireNonNull(clientId, "clientId is required");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret is required");
        this.ownerRefreshToken = Objects.requireNonNull(ownerRefreshToken, "ownerRefreshToken is required");
        this.rootFolderId = Objects.requireNonNull(rootFolderId, "rootFolderId is required");
        this.appName = appName == null ? "DOHIS" : appName;
        this.logs = logs;
        this.activityLogRepository = activityLogRepository;

    }

    public static class DownloadedFile {
        public final String fileName;
        public final String contentType;
        public final byte[] bytes;

        public DownloadedFile(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes;
        }
    }

    /** Construye un cliente Drive autenticado como el DUEÑO usando refresh_token */
    Drive ownerDrive() throws IOException {
        UserCredentials creds = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(ownerRefreshToken)
                .build();
        // Refresca si está expirado (la primera llamada ya se encarga si hace falta)
        creds.refreshIfExpired();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(creds))
                .setApplicationName(appName)
                .build();
    }

    private void log(String actor, String action, String targetId, String desc) {
        try {
            logs.save(ActivityLog.builder()
                    .userEmail(actor)
                    .action(action)
                    .targetId(targetId)
                    .description(desc)
                    .at(Instant.now())
                    .build());
        } catch (Exception ignored) {
            // No tumbar la operación si falló el log
        }
    }

    private String effectiveParent(String parentId) {
        return (parentId == null || parentId.isBlank()) ? rootFolderId : parentId;
    }

    /** Crear carpeta como OWNER */
    public File createFolderAsOwner(String name, String parentId, String actor) throws IOException {
        Drive drive = ownerDrive();

        File meta = new File();
        meta.setName(name);
        meta.setMimeType("application/vnd.google-apps.folder");
        meta.setParents(Collections.singletonList(effectiveParent(parentId)));

        File folder = drive.files()
                .create(meta)
                .setSupportsAllDrives(true)
                .setFields("id,name,parents")
                .execute();

        log(actor, "create_folder_owner", folder.getId(), "Created folder: " + name);
        return folder;
    }

    /** Listar hijos de una carpeta como OWNER */
    public List<File> listAsOwner(String parentId, String actor) throws IOException {
        Drive drive = ownerDrive();
        String pid = effectiveParent(parentId);
        String q = "'" + pid + "' in parents and trashed = false";

        FileList result = drive.files()
                .list()
                .setQ(q)
                .setIncludeItemsFromAllDrives(true)
                .setSupportsAllDrives(true)
                .setFields("files(id,name,mimeType,parents,modifiedTime,size,webViewLink)")
                .execute();

        log(actor, "list_owner", pid, "List children");
        return result.getFiles() == null ? Collections.emptyList() : result.getFiles();
    }

    /** Subir archivo como OWNER */
    public File uploadAsOwner(MultipartFile file, String parentId, String actor) throws IOException {
        Drive drive = ownerDrive();

        // Preparar metadata
        File meta = new File();
        meta.setName(file.getOriginalFilename());
        meta.setParents(Collections.singletonList(effectiveParent(parentId)));

        // Crear temp file
        java.io.File tmp = java.io.File.createTempFile("upload-", "-" + meta.getName());
        file.transferTo(tmp);

        String contentType = file.getContentType();
        FileContent media = new FileContent(contentType == null ? "application/octet-stream" : contentType, tmp);

        try {
            File created = drive.files()
                    .create(meta, media)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,parents,mimeType,webViewLink,webContentLink")
                    .execute();

            log(actor, "upload_owner", created.getId(), "Uploaded: " + created.getName());
            return created;
        } finally {
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (Exception ignored) {}
        }
    }

    public void deleteAsOwner(String fileId, String actorEmail) throws IOException {
        var drive = ownerDrive();

        // Elimina el archivo en Google Drive
        drive.files().delete(fileId).execute();

        // Si ya usas ActivityLog, registra la acción (ajusta nombres según tu proyecto)
        if (activityLogRepository != null) {
            activityLogRepository.save(
                    ActivityLog.builder()
                            .userEmail(actorEmail)
                            .action("DELETE_FILE")
                            .targetId(fileId)
                            .description("Archivo eliminado desde DOHIS")
                            .at(Instant.now())
                            .build()
            );
        }
    }

    public DownloadedFile downloadAsOwner(String fileId, String actorEmail) throws IOException {
        var drive = ownerDrive();

        // Obtenemos metadata básica
        com.google.api.services.drive.model.File fileMeta = drive.files()
                .get(fileId)
                .setFields("id,name,mimeType")
                .execute();

        String mimeType = fileMeta.getMimeType();
        String fileName = fileMeta.getName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "archivo";
        }

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        // Si es un archivo "nativo" de Google (Docs, Sheets, etc.), lo exportamos como PDF
        if (mimeType != null && mimeType.startsWith("application/vnd.google-apps.")) {
            String exportMime = "application/pdf";
            drive.files()
                    .export(fileId, exportMime)
                    .executeMediaAndDownloadTo(out);
            mimeType = exportMime;
        } else {
            // Archivos subidos normales (pdf, docx, etc.)
            drive.files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(out);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }
        }

        // Registrar actividad si tienes ActivityLogRepository
        try {
            if (activityLogRepository != null) {
                activityLogRepository.save(
                        ActivityLog.builder()
                                .userEmail(actorEmail)
                                .action("DOWNLOAD_FILE")
                                .targetId(fileId)
                                .description("Archivo descargado desde DOHIS")
                                .at(java.time.Instant.now())
                                .build()
                );
            }
        } catch (Exception ignored) {
            // No rompemos la descarga por un problema de logging
        }

        return new DownloadedFile(fileName, mimeType, out.toByteArray());
    }


    public boolean isWithinAllowedRoots(String folderId, Set<String> allowedRootFolders) throws IOException {
        if (allowedRootFolders == null || allowedRootFolders.isEmpty()) {
            return false;
        }
        if (!org.springframework.util.StringUtils.hasText(folderId)) {
            return false;
        }

        var drive = ownerDrive();
        String currentId = folderId;

        for (int i = 0; i < 20; i++) {
            if (allowedRootFolders.contains(currentId)) {
                return true;
            }

            com.google.api.services.drive.model.File f = drive.files()
                    .get(currentId)
                    .setFields("id, parents")
                    .execute();

            java.util.List<String> parents = f.getParents();
            if (parents == null || parents.isEmpty()) {
                break;
            }
            currentId = parents.get(0);
        }

        return false;
    }


    public File uploadTextFileToFolderAsOwner(String parentId, String fileName, String content, String actor) throws IOException {
        Drive drive = ownerDrive(); // ⬅️ usamos el método que ya tienes

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(fileName);
        // si quieres respetar el helper effectiveParent, puedes usarlo:
        fileMetadata.setParents(Collections.singletonList(
                parentId == null || parentId.isBlank() ? rootFolderId : parentId
        ));
        fileMetadata.setMimeType("text/plain");

        ByteArrayContent mediaContent =
                new ByteArrayContent("text/plain", content.getBytes(StandardCharsets.UTF_8));

        File created = drive.files()
                .create(fileMetadata, mediaContent)
                .setSupportsAllDrives(true)
                .setFields("id, name, parents, mimeType, webViewLink")
                .execute();

        // si quieres dejar log interno usando tu método log(...)
        log(actor, "upload_text_report", created.getId(), "Uploaded text report: " + fileName);

        return created;
    }






}
