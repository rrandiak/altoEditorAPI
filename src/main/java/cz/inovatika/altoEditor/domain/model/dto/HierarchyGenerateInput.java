package cz.inovatika.altoEditor.domain.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.domain.enums.HierarchyGenerateScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-serializable input for GENERATE_FOR_HIERARCHY batches.
 * Stored in {@link cz.inovatika.altoEditor.domain.model.Batch#data}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HierarchyGenerateInput {

    @JsonProperty("scope")
    private HierarchyGenerateScope scope;
}
