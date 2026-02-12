package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;


import java.time.LocalDateTime;

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

        private LocalDateTime started;
        private LocalDateTime finished;
        private LocalDateTime planned;

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

        private LocalDateTime started;
        private LocalDateTime finished;
        private LocalDateTime planned;

        private String token;
    }
}
