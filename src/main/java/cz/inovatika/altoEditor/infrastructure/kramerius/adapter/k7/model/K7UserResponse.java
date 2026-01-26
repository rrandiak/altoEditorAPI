package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7UserResponse {
    
    @JsonProperty("uid")
    private String uid;
    
    @JsonProperty("roles")
    private List<String> roles;
}
