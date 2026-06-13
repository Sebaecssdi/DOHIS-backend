package com.dohis.app.dto;

import com.dohis.app.security.Role;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserRequest {

    private String email;
    private String name;
    private Role role;                 // STANDARD / AREA_LEAD / ADMIN / OWNER
    private String area;               // Ej: RRHH, Finanzas...
    private Set<String> allowedRootFolders; // Carpetas raíz permitidas (misma estructura que update)
}
