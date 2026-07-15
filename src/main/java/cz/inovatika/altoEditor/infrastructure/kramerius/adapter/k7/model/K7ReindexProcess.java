package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

/**
 * K7 admin process definition for the indexer ({@code new_indexer_index_object}).
 *
 * <p>The current API takes a single {@code pid} (or a {@code ;}-joined list of pids
 * handled by one process) plus a {@code type} and {@code ignoreInconsistentObjects};
 * there is no separate {@code pidlist} parameter.
 */
@Getter
public class K7ReindexProcess {

    private final String defid = "new_indexer_index_object";
    private final Params params;

    public K7ReindexProcess(ReindexType reindexType, String pid) {
        this.params = new Params(reindexType, pid);
    }

    public K7ReindexProcess(ReindexType reindexType, List<String> pids) {
        this.params = new Params(reindexType, String.join(";", pids));
    }

    /**
     * Converts to JSON string
     */
    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public enum ReindexType {
        OBJECT,
        TREE_AND_FOSTER_TREES
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Params {
        @JsonProperty("type")
        private final ReindexType reindexType;

        @JsonProperty("pid")
        private final String pid;

        @JsonProperty("ignoreInconsistentObjects")
        private final boolean ignoreInconsistentObjects = true;

        public Params(ReindexType reindexType, String pid) {
            this.reindexType = reindexType;
            this.pid = pid;
        }
    }
}
