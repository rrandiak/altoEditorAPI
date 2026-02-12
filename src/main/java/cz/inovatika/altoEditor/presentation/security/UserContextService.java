package cz.inovatika.altoEditor.presentation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.exception.UnauthorizedUserException;
import cz.inovatika.altoEditor.exception.UserNotFoundException;

/**
 * Access to the current request's authenticated user (principal is {@link UserProfile}).
 *
 * @throws UnauthorizedUserException when no authenticated user
 * @throws RuntimeException from {@link #getUserId()} when user is not yet in local DB (e.g. first login)
 */
@Service
public class UserContextService {
    
    public UserProfile getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserProfile) {
            return (UserProfile) authentication.getPrincipal();
        }
        
        throw new UnauthorizedUserException("No authenticated user found");
    }
    
    public String getToken() {
        return getCurrentUser().getToken();
    }

    /** Local DB user ID; throws if user not yet synced (e.g. call {@code POST /api/users/me} first). */
    public Long getUserId() {
        if (getCurrentUser().getUserId() == null) {
            throw new UserNotFoundException(getCurrentUser().getUsername());
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