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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDto {

    private Integer id;
    private String pid;
    private String instance;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
    private BatchState state;
    private BatchSubstate substate;
    private BatchPriority priority;
    private BatchType type;
    private Integer objectId;
    private Integer estimatedItemCount;
    private String log;
}
