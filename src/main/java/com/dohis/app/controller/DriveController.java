package com.dohis.app.controller;

import com.dohis.app.model.User;
import com.dohis.app.repository.UserRepository;
import com.dohis.app.security.Role;
import com.dohis.app.service.DriveService;
import com.dohis.app.service.ActivityLogService; // ⬅️ NUEVO
import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.dohis.app.model.FilePermission;
import com.dohis.app.repository.FilePermissionRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/drive")
public class DriveController {

    private final DriveService driveService;
    private final UserRepository userRepository;
    private final FilePermissionRepository filePermissionRepository;
    private final ActivityLogService activityLogService; // ⬅️ NUEVO
    private final String rootFolderId;

    public DriveController(
            DriveService driveService,
            UserRepository userRepository,
            FilePermissionRepository filePermissionRepository,
            ActivityLogService activityLogService,               // ⬅️ NUEVO
            @Value("${app.drive.rootFolderId}") String rootFolderId
    ) {
        this.driveService = driveService;
        this.userRepository = userRepository;
        this.filePermissionRepository = filePermissionRepository;
        this.activityLogService = activityLogService;            // ⬅️ NUEVO
        this.rootFolderId = rootFolderId;
    }

    private String actor(Authentication auth) {
        if (auth == null) return "anonymous";
        if (auth.getName() != null) return auth.getName();
        return "unknown";
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            throw new RuntimeException("Usuario no autenticado");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + auth.getName()));
    }

    private boolean isAdminOrOwner(User u) {
        return u.getRole() == Role.ADMIN || u.getRole() == Role.OWNER;
    }

    /**
     * Devuelve true si el usuario puede ver una carpeta hija de la raíz.
     * OWNER/ADMIN → siempre true
     * AREA_LEAD/STANDARD → solo si el id está en allowedRootFolders
     */
    private boolean canSeeRootChild(User u, File f) {
        if (isAdminOrOwner(u)) return true;

        // En la raíz sólo queremos mostrar carpetas
        if (!"application/vnd.google-apps.folder".equals(f.getMimeType())) {
            return false;
        }
        if (u.getAllowedRootFolders() == null || u.getAllowedRootFolders().isEmpty()) {
            return false;
        }
        return u.getAllowedRootFolders().contains(f.getId());
    }

    // ========= LISTAR (READ-ONLY) =========
    @PreAuthorize("hasAnyRole('STANDARD','AREA_LEAD','ADMIN','OWNER')")
    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String parentId,
            Authentication auth
    ) {
        try {
            User user = currentUser(auth);

            if (!StringUtils.hasText(parentId)) {
                parentId = rootFolderId;
            }

            // Archivos/carpeta según Drive (como dueño)
            var files = driveService.listAsOwner(parentId, actor(auth));

            // 🔹 Obtener permisos explícitos de este usuario
            var userPerms = filePermissionRepository.findByUserId(user.getId());
            Set<String> permittedFileIds = userPerms.stream()
                    .filter(p -> p.isCanView() || p.isCanEdit() || p.isCanDelete())
                    .map(FilePermission::getFileId)
                    .collect(Collectors.toSet());

            // Si está listando la raíz:
            //  - sigue aplicando allowedRootFolders
            //  - PERO ahora también deja ver lo que tenga compartido por permisos
            if (rootFolderId.equals(parentId)) {
                files = files.stream()
                        .filter(f -> canSeeRootChild(user, f) || permittedFileIds.contains(f.getId()))
                        .collect(Collectors.toList());
            }

            List<Map<String, Object>> data = new ArrayList<>();
            for (File f : files) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", f.getId());
                row.put("name", f.getName());
                row.put("mimeType", f.getMimeType());
                row.put("parents", f.getParents());
                row.put("modifiedTime", f.getModifiedTime());
                row.put("webViewLink", f.getWebViewLink());
                data.add(row);
            }

            // 🔹 Log de listado (no crítico, pero útil para auditoría)
            activityLogService.log(
                    auth,
                    "LIST_FILES",
                    parentId,
                    "Listó contenido de la carpeta " + parentId
            );

            return ResponseEntity.ok(Map.of("items", data));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    // ========= CREAR CARPETA (WRITE) =========
    // OWNER y ADMIN pueden crear en cualquier lado.
    // AREA_LEAD solo puede crear dentro de sus allowedRootFolders (su área).
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    @PostMapping("/folders")
    public ResponseEntity<?> createFolder(
            @RequestParam String name,
            @RequestParam(required = false) String parentId,
            Authentication auth
    ) {
        try {
            if (!StringUtils.hasText(name)) {
                return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
            }

            User user = currentUser(auth);

            if (!StringUtils.hasText(parentId)) {
                parentId = rootFolderId;
            }

            // OWNER / ADMIN → siempre ok
            if (!isAdminOrOwner(user)) {
                // AREA_LEAD → solo si la carpeta está dentro de sus raíces permitidas
                if (user.getRole() == Role.AREA_LEAD) {
                    boolean allowed = driveService.isWithinAllowedRoots(parentId, user.getAllowedRootFolders());
                    if (!allowed) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "No tienes permisos para crear carpetas en esta ubicación."));
                    }
                } else {
                    // STANDARD u otro no debería llegar por la anotación, pero por si acaso
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "No tienes permisos para crear carpetas."));
                }
            }

            File folder = driveService.createFolderAsOwner(name, parentId, actor(auth));

            // 🔹 Log de creación de carpeta
            activityLogService.log(
                    auth,
                    "CREATE_FOLDER",
                    folder.getId(),
                    "Creó carpeta '" + folder.getName() + "' en parent " + parentId
            );

            return ResponseEntity.ok(Map.of(
                    "id", folder.getId(),
                    "name", folder.getName(),
                    "parents", folder.getParents()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear carpeta: " + e.getMessage()));
        }
    }

    // ========= SUBIR ARCHIVO (WRITE) =========
    // OWNER y ADMIN pueden subir en cualquier lado.
    // AREA_LEAD solo puede subir dentro de sus allowedRootFolders (su área).
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String parentId,
            Authentication auth
    ) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
            }

            User user = currentUser(auth);

            if (!StringUtils.hasText(parentId)) {
                parentId = rootFolderId;
            }

            // OWNER / ADMIN → siempre ok
            if (!isAdminOrOwner(user)) {
                // AREA_LEAD → solo si la carpeta está dentro de alguna de sus raíces permitidas
                if (user.getRole() == Role.AREA_LEAD) {
                    boolean allowed = driveService.isWithinAllowedRoots(parentId, user.getAllowedRootFolders());
                    if (!allowed) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "No tienes permisos para subir archivos en esta carpeta."));
                    }
                } else {
                    // STANDARD (u otro) no debería llegar aquí por la anotación, pero por si acaso:
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "No tienes permisos para subir archivos."));
                }
            }

            File created = driveService.uploadAsOwner(file, parentId, actor(auth));

            // 🔹 Log de subida de archivo
            activityLogService.log(
                    auth,
                    "UPLOAD_FILE",
                    created.getId(),
                    "Subió el archivo '" + created.getName() + "' en carpeta " + parentId
            );

            return ResponseEntity.ok(Map.of(
                    "id", created.getId(),
                    "name", created.getName(),
                    "parents", created.getParents(),
                    "mimeType", created.getMimeType(),
                    "webViewLink", created.getWebViewLink()
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error de I/O al subir archivo: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ========= ELIMINAR ARCHIVO / CARPETA =========
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<?> deleteFile(
            @PathVariable String fileId,
            Authentication auth
    ) {
        try {
            driveService.deleteAsOwner(fileId, actor(auth));

            // 🔹 Log de eliminación
            activityLogService.log(
                    auth,
                    "DELETE_FILE",
                    fileId,
                    "Eliminó archivo/carpeta " + fileId
            );

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ========= PERMISOS DE ARCHIVO =========

    public static class FilePermissionDto {
        public String userId;
        public boolean canView;
        public boolean canEdit;
        public boolean canDelete;
    }

    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    @GetMapping("/files/{fileId}/permissions")
    public ResponseEntity<?> getFilePermissions(
            @PathVariable String fileId,
            Authentication auth
    ) {
        try {
            User current = currentUser(auth);
            // (Opcional) validar que current tenga acceso al archivo

            List<FilePermission> perms = filePermissionRepository.findByFileId(fileId);

            List<FilePermissionDto> dto = perms.stream()
                    .map(p -> {
                        FilePermissionDto d = new FilePermissionDto();
                        d.userId = p.getUserId();
                        d.canView = p.isCanView();
                        d.canEdit = p.isCanEdit();
                        d.canDelete = p.isCanDelete();
                        return d;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    @PutMapping("/files/{fileId}/permissions")
    public ResponseEntity<?> updateFilePermissions(
            @PathVariable String fileId,
            @RequestBody List<FilePermissionDto> permissions,
            Authentication auth
    ) {
        try {
            User current = currentUser(auth);
            // (Opcional) validar que current pueda gestionar permisos de este archivo

            // Borramos permisos actuales del archivo
            filePermissionRepository.deleteByFileId(fileId);

            // Creamos nuevos (solo si hay al menos un permiso en true)
            List<FilePermission> toSave = permissions.stream()
                    .filter(p -> p.canView || p.canEdit || p.canDelete)
                    .map(p -> FilePermission.builder()
                            .fileId(fileId)
                            .userId(p.userId)
                            .canView(p.canView)
                            .canEdit(p.canEdit)
                            .canDelete(p.canDelete)
                            .build()
                    )
                    .collect(Collectors.toList());

            if (!toSave.isEmpty()) {
                filePermissionRepository.saveAll(toSave);
            }

            // 🔹 Log de actualización de permisos
            activityLogService.log(
                    auth,
                    "UPDATE_PERMISSIONS",
                    fileId,
                    "Actualizó permisos de archivo/carpeta " + fileId
            );

            return ResponseEntity.ok(Map.of("ok", true));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
