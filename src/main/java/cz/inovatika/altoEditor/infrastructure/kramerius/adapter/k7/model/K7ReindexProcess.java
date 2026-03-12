package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class K7ReindexProcess {

    private final String defid = "new_indexer_index_object";
    private final Params params;

    public K7ReindexProcess(ReindexType reindexType, String pid) {
        this.params = new Params(reindexType, pid, null);
    }

    public K7ReindexProcess(ReindexType reindexType, List<String> pids) {
        this.params = new Params(reindexType, null, pids);
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

    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Params {
        @JsonProperty("type")
        private final ReindexType reindexType;

        @JsonProperty("pid")
        private String pid;

        @JsonProperty("pidlist")
        private List<String> pidList;
    }
}
