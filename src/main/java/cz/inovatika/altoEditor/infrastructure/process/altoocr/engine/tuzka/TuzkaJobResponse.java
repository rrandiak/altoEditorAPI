package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * taas job payload as returned by {@code POST /api/v1/jobs} (202) and {@code GET /api/v1/jobs/{job_id}}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TuzkaJobResponse {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("status")
    private String status;
}
