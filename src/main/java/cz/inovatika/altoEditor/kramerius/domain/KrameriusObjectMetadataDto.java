package cz.inovatika.altoEditor.kramerius.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class KrameriusObjectMetadataDto {

    private final String pid;
    private final String title;
    private final String parentPath;
    private final String parentTitle;
}
