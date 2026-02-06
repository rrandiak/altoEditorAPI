package cz.inovatika.altoEditor.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Single hit in hierarchy search (local index + repository-backed page counts). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HierarchySearchDto {

    private String pid;
    private String model;
    private String title;
    private Integer level;

    private String parentPid;
    private Integer indexInParent;
    private String rootPid;

    private Integer pagesCount;
    private Integer pagesWithAlto;
}
