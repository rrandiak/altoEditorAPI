package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import java.util.UUID;

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
    private final String rootPid;

    private UUID parsePid(String pid) {
        String[] parts = pid.split(":");
        return UUID.fromString(parts[1]);
    }
    
    public UUID getUuid() {
        return parsePid(pid);
    }

    public UUID getParentUuid() {
        return parsePid(parentPid);
    }
}
