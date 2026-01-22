package cz.inovatika.altoEditor.api.auth;

import java.util.List;

import cz.inovatika.altoEditor.core.enums.Role;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class UserProfile {
    
    private String token;
    private Integer userId;
    private String username;
    private List<Role> roles;
}
