package cz.inovatika.altoEditor.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import lombok.Data;

/** Search request for curator ALTO version search (all versions). */
@Data
public class AltoVersionSearchRequest {

    private List<Long> users;

    private String instance;

    private String targetPid;
    private String hierarchyPid;

    private String title;

    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;

    private List<AltoVersionState> states;

    private int offset = 0;
    private int limit = 10;
}