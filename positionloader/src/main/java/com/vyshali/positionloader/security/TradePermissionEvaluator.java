package com.vyshali.positionloader.security;

/*
 * 12/04/2025 - 1:41 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class TradePermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        // In a real bank, this queries an Entitlements DB (OPA/XACML)
        // Here we simulate checking if the user's "Region" matches the Account's region

        String requiredRegion = (String) targetDomainObject; // e.g., "EMEA"

        // Example: Only "EMEA_TRADER" role can touch EMEA accounts
        String requiredRole = requiredRegion + "_TRADER";

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals(requiredRole) || role.equals("ROLE_SUPER_ADMIN"));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return false;
    }
}