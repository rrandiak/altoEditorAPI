package cz.inovatika.altoEditor.kramerius.k7.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7PlanProcessResponse {

    private String name;

    private String id;

    private String state;

    private OffsetDateTime planned;

    private String uuid;
}
