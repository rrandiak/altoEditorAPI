package cz.inovatika.altoEditor.domain.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search/filter criteria for ALTO versions.
 * Used for search API, ACCEPT_VERSIONS batch data (JSON in {@link cz.inovatika.altoEditor.domain.model.Batch#data}),
 * and {@link cz.inovatika.altoEditor.domain.service.AltoVersionService#search} / {@link cz.inovatika.altoEditor.domain.service.AltoVersionService#findVersionIdsByFilter}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AltoVersionSearchFilter {

    @JsonProperty("users")
    private List<Long> users;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("targetPid")
    private String targetPid;

    @JsonProperty("hierarchyPid")
    private String hierarchyPid;

    @JsonProperty("title")
    private String title;

    @JsonProperty("createdAfter")
    private LocalDateTime createdAfter;

    @JsonProperty("createdBefore")
    private LocalDateTime createdBefore;

    @JsonProperty("states")
    private List<AltoVersionState> states;

    @JsonProperty("sortBy")
    @Builder.Default
    private String sortBy = "updatedAt";

    /** "ASC" or "DESC" */
    @JsonProperty("sortOrder")
    @Builder.Default
    private String sortOrder = "DESC";

    @JsonProperty("offset")
    @Builder.Default
    private int offset = 0;

    @JsonProperty("limit")
    @Builder.Default
    private int limit = 10;
}
