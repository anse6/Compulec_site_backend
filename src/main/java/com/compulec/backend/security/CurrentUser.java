package com.compulec.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Accès au rôle de l'utilisateur courant (y compris sur les routes publiques,
 * où le filtre JWT authentifie quand même un visiteur qui envoie un jeton valide).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** true si l'appelant est un ADMIN ou un MANAGER connecté. */
    public static boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_MANAGER".equals(a.getAuthority()));
    }
}
