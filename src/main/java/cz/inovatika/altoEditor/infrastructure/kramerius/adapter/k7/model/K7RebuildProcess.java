package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * K7 admin process definition for rebuilding the processing index of an object
 * (and its subtree). Run before {@link K7ReindexProcess} after datastreams change,
 * so the main index picks up the refreshed processing metadata.
 *
 * <p>The {@code target} is a single pid or a {@code ;}-joined list of pids handled
 * by one process (same shape as the reindex process's pid string).
 */
@Getter
public class K7RebuildProcess {

    private final String defid = "processing_rebuild_for_object";
    private final Params params;

    public K7RebuildProcess(String target) {
        this.params = new Params(target);
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Params {
        @JsonProperty("target")
        private final String target;
    }
}
