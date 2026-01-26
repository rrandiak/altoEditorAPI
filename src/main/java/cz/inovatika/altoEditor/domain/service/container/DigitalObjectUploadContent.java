package cz.inovatika.altoEditor.domain.service.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class DigitalObjectUploadContent {
    private String pid;
    private String instance;
    private byte[] altoContent;
    private byte[] ocrContent;
}
