package cz.inovatika.altoEditor.presentation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.domain.enums.Role;

@Service
public class UserContextService {
    
    public UserProfile getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserProfile) {
            return (UserProfile) authentication.getPrincipal();
        }
        
        // TODO: Replace with proper exception handling
        throw new RuntimeException("No authenticated user found");
        // throw new UnauthorizedException("User not authenticated");
    }
    
    public String getToken() {
        return getCurrentUser().getToken();
    }

    public Integer getUserId() {
        if (getCurrentUser().getUserId() == null) {
            throw new RuntimeException("User ID is not available");
        }
        return getCurrentUser().getUserId();
    }

    public String getUid() {
        return getCurrentUser().getUid();
    }
    
    public String getUsername() {
        return getCurrentUser().getUsername();
    }
    
    public boolean hasRole(Role role) {
        return getCurrentUser().getRoles().contains(role);
    }
}