package cz.inovatika.altoEditor.core.service.container;

import cz.inovatika.altoEditor.core.entity.DigitalObject;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DigitalObjectWithContent {
    private DigitalObject digitalObject;
    private String content;
}