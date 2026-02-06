package cz.inovatika.altoEditor.presentation.security;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Principal held in security context after JWT validation (Kramerius user + optional local user id). */
@Getter
@AllArgsConstructor
public class UserProfile {
    
    private String token;
    private Long userId;
    private String uid;
    private String username;
    private List<Role> roles;
}
