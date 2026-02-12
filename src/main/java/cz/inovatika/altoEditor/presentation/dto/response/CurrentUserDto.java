package cz.inovatika.altoEditor.presentation.dto.response;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDto {
    
    private Long id;
    private String username;
    private List<Role> roles;
}
