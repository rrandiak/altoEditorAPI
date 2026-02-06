package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** User profile response (editor/curator or Kramerius/engine user). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    
    private Integer id;
    private String username;

    private Boolean isKramerius;
    private Boolean isEngine;
    private Boolean isEnabled;
}
