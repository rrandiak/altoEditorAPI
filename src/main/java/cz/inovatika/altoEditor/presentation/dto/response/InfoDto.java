package cz.inovatika.altoEditor.presentation.dto.response;

import java.util.List;

import lombok.Data;

/** System / API info (e.g. version). */
@Data
public class InfoDto {
    
    private String version = "2.0.0";
    private List<KrameriusInstance> instances;
}
