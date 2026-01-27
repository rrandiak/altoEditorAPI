package cz.inovatika.altoEditor.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import lombok.Data;

@Data
public class DigitalObjectSearchRequest {

    private List<Integer> users;

    private String instanceId;

    private String targetPid;
    private String hierarchyPid;

    private String label;
    private String title;

    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;

    private List<DigitalObjectState> states;
}