package cz.inovatika.altoEditor.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ALTO version hit in search results (curator or related search). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AltoVersionSearchDto {

    private Long id;
    private String pid;
    private Integer version;
    private List<String> presentInInstances;
    private String username;
    private AltoVersionState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String pageTitle;
    private Integer pageIndex;
    private List<String> ancestorPids;
    private List<String> ancestorTitles;
}
