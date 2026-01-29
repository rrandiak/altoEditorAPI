package cz.inovatika.altoEditor.presentation.dto.request;

import lombok.Data;

@Data
public class ObjectHierarchySearchRequest {
    
    private String pid;
    private String parentPid;
    private String model;
    private String title;
    private Short level;
    private Boolean hasAlto;
}
