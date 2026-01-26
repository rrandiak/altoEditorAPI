package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;


import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7ProcessBatch {

    private Process process;
    private Batch batch;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Process {
        private String defid;
        private String name;
        private String id;
        private String state;

        private OffsetDateTime started;
        private OffsetDateTime finished;
        private OffsetDateTime planned;

        private String uuid;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Batch {
        @JsonProperty("owner_name")
        private String ownerName;

        @JsonProperty("owner_id")
        private String ownerId;

        private String id;
        private String state;

        private OffsetDateTime started;
        private OffsetDateTime finished;
        private OffsetDateTime planned;

        private String token;
    }
}
