package cz.inovatika.altoEditor.presentation.dto.request;

import lombok.Data;

/** Search request for object hierarchy (offset/limit pagination). */
@Data
public class ObjectHierarchySearchRequest {
    
    private String pid;
    private String parentPid;
    private String model;
    private String title;
    private Integer level;

    private int offset = 0;
    private int limit = 10;
}
