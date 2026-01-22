package cz.inovatika.altoEditor.kramerius.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadAltoOcrResponse {
    private final String processUuid;
    private final String processLink;
}
