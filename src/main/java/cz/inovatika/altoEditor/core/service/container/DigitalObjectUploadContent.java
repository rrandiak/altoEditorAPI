package cz.inovatika.altoEditor.core.service.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class DigitalObjectUploadContent {
    private String pid;
    private String instance;
    private String altoContent;
    private String ocrContent;
}
