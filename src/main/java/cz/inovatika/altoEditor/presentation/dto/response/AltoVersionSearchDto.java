package cz.inovatika.altoEditor.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;

public class AltoVersionSearchDto {

    Long id;

    String pid;
    Integer version;
    String instance;

    String username;

    AltoVersionState state;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    String pageTitle;
    Integer pageIndex;

    List<String> ancestorPids;
    List<String> ancestorTitles;
}
