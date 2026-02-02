package cz.inovatika.altoEditor.domain.service.container;

import cz.inovatika.altoEditor.domain.model.AltoVersion;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AltoVersionWithContent {
    private AltoVersion altoVersion;
    private byte[] content;
}