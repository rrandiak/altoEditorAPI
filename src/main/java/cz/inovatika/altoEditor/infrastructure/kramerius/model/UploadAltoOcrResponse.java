package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadAltoOcrResponse {
    private final String processUuid;
    private final String processLink;
}
