package cz.inovatika.altoEditor.domain.service.container;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DigitalObjectWithContent {
    private DigitalObject digitalObject;
    private byte[] content;
}