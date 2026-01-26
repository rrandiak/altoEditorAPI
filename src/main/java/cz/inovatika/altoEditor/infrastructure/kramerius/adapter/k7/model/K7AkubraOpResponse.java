package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.domain.enums.Datastream;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7AkubraOpResponse {

    @JsonProperty("dsId")
    private Datastream dsId;
}
