package com.dohis.app.dto;

import com.dohis.app.security.Role;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateUserRequest {

    private String name;
    private Role role;
    private String area;
    private Set<String> allowedRootFolders;
}
