package cz.inovatika.altoEditor.domain.service.container;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Engine {
    
    private String name;
    private boolean enabled;
}
