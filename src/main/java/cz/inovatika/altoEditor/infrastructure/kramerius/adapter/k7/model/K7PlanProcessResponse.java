package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7PlanProcessResponse {

    private String name;

    private String id;

    private String state;

    // Kramerius returns timestamp of wrong format
    private String planned;

    private String uuid;
}
