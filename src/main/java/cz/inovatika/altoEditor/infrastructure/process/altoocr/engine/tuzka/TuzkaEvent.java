package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * A taas WebSocket event ({@code /ws}). Terminal events carry the job id, the caller's
 * external id and, on success, presigned result URLs:
 * <pre>
 * {"job_id":"...","external_id":"...","status":"done","alto_url":"...","txt_url":"..."}
 * {"job_id":"...","external_id":"...","status":"failed","error":"..."}
 * </pre>
 * taas before 0.9 sent the external id as {@code uuid} (and no job id); it's read too.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TuzkaEvent {

    @JsonProperty("status")
    private String status;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("external_id")
    @JsonAlias("uuid")
    private String externalId;

    @JsonProperty("alto_url")
    private String altoUrl;

    @JsonProperty("txt_url")
    private String txtUrl;

    @JsonProperty("error")
    private String error;
}
