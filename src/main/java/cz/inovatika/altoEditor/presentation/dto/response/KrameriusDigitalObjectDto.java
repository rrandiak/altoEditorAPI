package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Digital object metadata from Kramerius (with children/pages counts). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KrameriusDigitalObjectDto {

    private String pid;
    private String model;
    private String title;
    private Integer level;

    private Integer childrenCount;
    private Integer pagesCount;

    private String parentPid;
    private String rootPid;
}
