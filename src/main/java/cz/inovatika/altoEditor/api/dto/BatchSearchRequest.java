package cz.inovatika.altoEditor.api.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import cz.inovatika.altoEditor.core.enums.BatchPriority;
import cz.inovatika.altoEditor.core.enums.BatchState;
import cz.inovatika.altoEditor.core.enums.BatchSubstate;
import cz.inovatika.altoEditor.core.enums.BatchType;

import java.time.LocalDateTime;

@Data
public class BatchSearchRequest {

    private String pid;
    private BatchState state;
    private BatchSubstate substate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdBefore;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedBefore;

    private BatchPriority priority;
    private BatchType type;
    private String instance;
}