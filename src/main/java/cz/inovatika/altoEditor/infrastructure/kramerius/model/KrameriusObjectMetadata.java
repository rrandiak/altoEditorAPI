package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class KrameriusObjectMetadata {

    private final String pid;
    private final String model;
    private final String title;
    private final Integer level;
    private final Integer indexInParent;
    private final String parentPid;
}
