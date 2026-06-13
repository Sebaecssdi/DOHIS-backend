package com.dohis.app.security;

import java.util.Set;

public record Permissions(Set<String> allowedPaths) {
}
