package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search document for hierarchy search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HierarchySearchDto {

    private String pid;
    private String model;
    private String title;
    private Integer level;

    private Integer pagesCount;
    private Integer pagesWithAlto;

    private String parentPid;
    private String rootPid;
}
