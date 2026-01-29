package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class KrameriusObjectMetadata {

    private final String pid;
    private final String model;
    private final String title;
    private final Integer indexInParent;
    private final String parentPid;
    private final String parentModel;
    private final String parentTitle;
    private final String rootTitle;
}
