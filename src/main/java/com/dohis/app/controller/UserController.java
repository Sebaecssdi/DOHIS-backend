package com.dohis.app.controller;

import com.dohis.app.dto.CreateUserRequest;
import com.dohis.app.dto.UpdateUserRequest;
import com.dohis.app.model.User;
import com.dohis.app.security.Role;
import com.dohis.app.service.UserService;
import com.dohis.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private final UserService userService;

    @Autowired
    private final UserRepository userRepository;

    public UserController(UserService userService,
                          UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /** Devuelve el usuario actual */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        try {
            User u = userService.getByEmail(auth.getName());
            return ResponseEntity.ok(u);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Devuelve los subordinados según rol:
     * OWNER → todos
     * ADMIN → todos excepto OWNER
     * AREA_LEAD → STANDARD de su área
     * STANDARD → 403
     */
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AREA_LEAD')")
    @GetMapping("/subordinates")
    public ResponseEntity<?> subordinates(Authentication auth) {
        try {
            User current = userService.getByEmail(auth.getName());
            List<User> subs = userService.getSubordinates(current);
            return ResponseEntity.ok(subs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AREA_LEAD')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request,
            Authentication auth
    ) {
        try {
            User current = userService.getByEmail(auth.getName());

            User target = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));

            User updated = userService.updateUser(current, target, request);
            return ResponseEntity.ok(updated);

        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping
    public ResponseEntity<?> createUser(
            @RequestBody CreateUserRequest request,
            Authentication auth
    ) {
        try {
            User current = userService.getByEmail(auth.getName());

            User created = userService.createUser(
                    current,
                    request.getEmail(),
                    request.getName(),
                    request.getRole(),
                    request.getArea(),
                    request.getAllowedRootFolders()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<?> deleteUser(
            @PathVariable String userId,
            Authentication authentication
    ) {
        try {
            String currentEmail = authentication != null ? authentication.getName() : null;

            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Usuario no encontrado"));
            }

            var userToDelete = userOpt.get();

            // Evitar que se borre a sí mismo
            if (currentEmail != null && currentEmail.equalsIgnoreCase(userToDelete.getEmail())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No puedes eliminar tu propio usuario."));
            }

            userRepository.deleteById(userId);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar usuario: " + e.getMessage()));
        }
    }


}
