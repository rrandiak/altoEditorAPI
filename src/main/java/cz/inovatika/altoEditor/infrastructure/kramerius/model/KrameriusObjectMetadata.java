package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class KrameriusObjectMetadata {

    private final String pid;
    private final String title;
    private final String parentPath;
    private final String parentTitle;
}
