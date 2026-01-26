package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolrResponse<T> {
    private Response<T> response;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response<T> {
        private int numFound;
        private List<T> docs;
    }
}
