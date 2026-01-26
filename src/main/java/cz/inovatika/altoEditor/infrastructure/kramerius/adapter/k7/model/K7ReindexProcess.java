package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Getter
public class K7ReindexProcess {

    private final String defid = "new_indexer_index_object";
    private final Params params;

    public K7ReindexProcess(String pid) {
        this.params = new Params(pid);
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

    @Getter
    public static class Params {
        @JsonProperty("type")
        private final String reindexType = "OBJECT";

        @JsonProperty("pid")
        private String pid;

        public Params(String pid) {
            this.pid = pid;
        }
    }
}
