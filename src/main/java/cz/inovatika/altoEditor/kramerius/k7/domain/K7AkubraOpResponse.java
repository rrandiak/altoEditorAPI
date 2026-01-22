package cz.inovatika.altoEditor.kramerius.k7.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.core.enums.Datastream;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7AkubraOpResponse {

    @JsonProperty("dsId")
    private Datastream dsId;
}
