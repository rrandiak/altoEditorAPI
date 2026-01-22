package cz.inovatika.altoEditor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchDto {

    private Integer id;
    private String pid;
    private String instance;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;
    private String state;
    private String substate;
    private String priority;
    private String type;
    private Integer objectId;
    private Integer estimateItemNumber;
    private String log;
}
