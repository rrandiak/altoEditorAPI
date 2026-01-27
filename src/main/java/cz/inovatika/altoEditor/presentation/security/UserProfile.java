package cz.inovatika.altoEditor.presentation.security;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.Role;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class UserProfile {
    
    private String token;
    private Integer userId;
    private String uid;
    private String username;
    private List<Role> roles;
}
