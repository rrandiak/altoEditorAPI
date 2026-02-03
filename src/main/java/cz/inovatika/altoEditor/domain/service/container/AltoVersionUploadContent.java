package cz.inovatika.altoEditor.domain.service.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AltoVersionUploadContent {
    private String pid;
    private byte[] altoContent;
    private byte[] ocrContent;
}
