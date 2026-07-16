package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * A taas WebSocket event ({@code /ws}). Terminal events carry the caller's external id and,
 * on success, presigned result URLs:
 * <pre>
 * {"status":"done","uuid":"&lt;external_id&gt;","alto_url":"...","txt_url":"..."}
 * {"status":"failed","uuid":"&lt;external_id&gt;","error":"..."}
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TuzkaEvent {

    @JsonProperty("status")
    private String status;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("alto_url")
    private String altoUrl;

    @JsonProperty("txt_url")
    private String txtUrl;

    @JsonProperty("error")
    private String error;
}
