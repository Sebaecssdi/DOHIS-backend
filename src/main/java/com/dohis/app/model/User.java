package com.dohis.app.model;

import com.dohis.app.security.Role;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String email;
    private String name;
    private String avatar;
    private Role role;

    /**
     * Área principal del usuario: "RRHH", "MARKETING", etc.
     * Opcional para OWNER / ADMIN.
     */
    private String area;

    /**
     * IDs de carpetas de Drive (hijas de la raíz lógica)
     * que el usuario puede ver cuando se lista la raíz.
     *
     * - Para OWNER / ADMIN no es necesario rellenarlo.
     * - Para AREA_LEAD: IDs de las carpetas de su área.
     * - Para STANDARD: IDs de las carpetas específicas que puede ver.
     */
    private Set<String> allowedRootFolders;

    /**
     * Carpeta personal del usuario en Drive.
     * Se crea automáticamente para usuarios STANDARD dentro de la carpeta de su área.
     */
    private String personalFolderId;
}
