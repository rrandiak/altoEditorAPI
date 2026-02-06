package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.BatchSubstate;
import cz.inovatika.altoEditor.domain.enums.BatchType;

/** Batch job (ALTO generation, hierarchy retrieval, etc.) response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDto {

    private Integer id;

    private BatchType type;
    private BatchPriority priority;

    private String pid;
    private Long altoVersionId;
    private String instance;
    private String engine;

    private BatchState state;
    private BatchSubstate substate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer estimatedItemCount;

    private String log;
}
