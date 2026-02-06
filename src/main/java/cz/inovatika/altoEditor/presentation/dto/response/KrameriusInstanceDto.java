package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kramerius instance reference (name, enabled flag). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KrameriusInstanceDto {
    
    private String name;
    private Boolean enabled;
}
