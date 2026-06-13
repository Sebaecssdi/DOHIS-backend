package com.dohis.app.service;

import com.dohis.app.dto.UpdateUserRequest;
import com.dohis.app.model.User;
import com.dohis.app.repository.UserRepository;
import com.dohis.app.security.Role;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final DriveService driveService;
    public User createUser(
            User current,
            String email,
            String name,
            Role role,
            String area,
            Set<String> allowedRootFolders
    ) {

        // --- Validaciones de seguridad ---
        if (current.getRole() != Role.OWNER && current.getRole() != Role.ADMIN) {
            throw new RuntimeException("No tienes permisos para crear usuarios.");
        }

        if (email == null || email.isBlank()) {
            throw new RuntimeException("El email es obligatorio.");
        }

        // No permitir duplicados
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            throw new RuntimeException("Ya existe un usuario con ese email.");
        }

        // --- Crear el usuario ---
        User u = User.builder()
                .email(email)
                .name(name)
                .role(role != null ? role : Role.STANDARD)
                .area(area)
                .allowedRootFolders(allowedRootFolders)
                .build();

        return userRepository.save(u);
    }
    // Mapeo de área lógica -> carpeta raíz de Drive
    private static final Map<String, String> AREA_ROOT_FOLDERS = new HashMap<>();
    static {
        AREA_ROOT_FOLDERS.put("RRHH", "1S2E993W5TcCVR2NbszTebvAXBFBCbi_y");
        AREA_ROOT_FOLDERS.put("MARKETING", "1T7ypnZjaXhl-_IFzmHGV-3A03j6PNqiX");
        AREA_ROOT_FOLDERS.put("FINANZAS", "1xL6wN-nT-0Y8NTbNHNdUyhqQSqypd5lz");
        AREA_ROOT_FOLDERS.put("EJEMPLOASD", "1BPdVc5hG_CywBX9vPDCxLDFCBXiiOlvz");
        // agrega más áreas si las necesitas
    }

    public UserService(UserRepository userRepository, DriveService driveService) {
        this.userRepository = userRepository;
        this.driveService = driveService;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));
    }

    public List<User> getSubordinates(User current) {
        Role role = current.getRole();

        switch (role) {
            case OWNER:
                // Ve a todos MENOS otros OWNER (y a sí mismo)
                return userRepository.findAll().stream()
                        .filter(u -> u.getRole() != Role.OWNER)
                        .toList();

            case ADMIN:
                // Ve sólo a roles inferiores: AREA_LEAD y STANDARD
                return userRepository.findAll().stream()
                        .filter(u -> u.getRole() != Role.OWNER && u.getRole() != Role.ADMIN)
                        .toList();

            case AREA_LEAD:
                // Ve sólo STANDARD de su misma área
                return userRepository.findByRole(Role.STANDARD).stream()
                        .filter(u -> current.getArea() != null
                                && current.getArea().equalsIgnoreCase(u.getArea()))
                        .toList();

            default:
                return List.of();
        }
    }

    private Set<String> computeAllowedFolders(Role role, String area) {
        // OWNER y ADMIN no necesitan allowedRootFolders para el filtro de raíz
        if (role == Role.OWNER || role == Role.ADMIN) {
            return Set.of();
        }

        if (area == null) {
            return Set.of();
        }

        String rootId = AREA_ROOT_FOLDERS.get(area);
        if (rootId == null || rootId.isBlank()) {
            // área sin carpeta mapeada → sin permisos explícitos
            return Set.of();
        }

        // Por ahora: AREA_LEAD y STANDARD ven la carpeta raíz de su área
        return Set.of(rootId);
    }

    private String ensurePersonalFolder(User user, String areaRootId) {
        if (areaRootId == null || areaRootId.isBlank()) {
            return null;
        }

        if (user.getPersonalFolderId() != null && !user.getPersonalFolderId().isBlank()) {
            // Ya la tiene registrada
            return user.getPersonalFolderId();
        }

        try {
            // Nombre de la carpeta personal
            String baseName = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : user.getEmail();
            String folderName = "Personal - " + baseName;

            // Buscar si ya existe una carpeta con ese nombre bajo el área
            var drive = driveService.ownerDrive();

            String query = String.format(
                    "mimeType = 'application/vnd.google-apps.folder' " +
                            "and name = '%s' and '%s' in parents and trashed = false",
                    folderName.replace("'", "\\'"),
                    areaRootId
            );

            var list = drive.files()
                    .list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .execute();

            String folderId;
            if (list.getFiles() != null && !list.getFiles().isEmpty()) {
                folderId = list.getFiles().get(0).getId();
            } else {
                // No existe -> crearla
                var created = driveService.createFolderAsOwner(folderName, areaRootId, user.getEmail());
                folderId = created.getId();
            }

            // Guardar en el usuario
            user.setPersonalFolderId(folderId);
            userRepository.save(user);

            return folderId;

        } catch (Exception e) {
            e.printStackTrace();
            // Si falla, simplemente no seteamos carpeta personal
            return null;
        }
    }

    public User updateUser(User current, User target, UpdateUserRequest req) {

        Role currentRole = current.getRole();
        Role targetRole = target.getRole();

        // 🔒 Nadie puede modificarse a sí mismo
        if (current.getId() != null && current.getId().equals(target.getId())) {
            throw new RuntimeException("No puedes modificar tu propio usuario.");
        }

        // --- Validaciones por rol del que modifica ---

        // OWNER
        if (currentRole == Role.OWNER) {
            if (targetRole == Role.OWNER) {
                throw new RuntimeException("No puedes modificar usuarios de tu misma jerarquía.");
            }

        }
        // ADMIN
        else if (currentRole == Role.ADMIN) {
            if (targetRole == Role.OWNER || targetRole == Role.ADMIN) {
                throw new RuntimeException("No puedes modificar usuarios de tu misma jerarquía ni superiores.");
            }
            if (req.getRole() == Role.OWNER) {
                throw new RuntimeException("Un ADMIN no puede asignar rol OWNER.");
            }

        }
        // AREA_LEAD
        else if (currentRole == Role.AREA_LEAD) {

            if (targetRole != Role.STANDARD) {
                throw new RuntimeException("Solo puedes modificar usuarios STANDARD.");
            }

            if (current.getArea() == null
                    || !current.getArea().equalsIgnoreCase(target.getArea())) {
                throw new RuntimeException("Solo puedes modificar usuarios de tu misma área.");
            }

            if (req.getRole() != null && req.getRole() != Role.STANDARD) {
                throw new RuntimeException("No puedes cambiar el rol de STANDARD.");
            }

            if (req.getArea() != null
                    && !req.getArea().equalsIgnoreCase(current.getArea())) {
                throw new RuntimeException("No puedes cambiar el área del usuario.");
            }

        } else {
            throw new RuntimeException("No tienes permisos para modificar usuarios.");
        }

        // --- Aplicar cambios básicos (nombre, rol, área) ---

        if (req.getName() != null && !req.getName().isBlank()) {
            target.setName(req.getName());
        }

        if (req.getRole() != null) {
            target.setRole(req.getRole());
        }

        if (req.getArea() != null) {
            target.setArea(req.getArea());
        }

        // --- Recalcular allowedRootFolders según rol + área ---

        Role finalRole = target.getRole();
        String finalArea = target.getArea();

        Set<String> allowed = computeAllowedFolders(finalRole, finalArea);
        target.setAllowedRootFolders(allowed);

        // --- Crear carpeta personal para STANDARD ---

        if (finalRole == Role.STANDARD && finalArea != null) {
            String areaRootId = AREA_ROOT_FOLDERS.get(finalArea);
            if (areaRootId != null) {
                ensurePersonalFolder(target, areaRootId);
                // Ojo: la carpeta personal cuelga del área.
                // La visibilidad de la raíz sigue filtrada por allowedRootFolders (carpeta de área).
            }
        }

        return userRepository.save(target);
    }
}
