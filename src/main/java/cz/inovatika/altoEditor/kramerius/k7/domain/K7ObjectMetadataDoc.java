package cz.inovatika.altoEditor.kramerius.k7.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.kramerius.domain.KrameriusObjectMetadataDto;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K7ObjectMetadataDoc {

    @JsonProperty("pid")
    private String pid;
    
    @JsonProperty("title.search")
    private String titleSearch;
    
    @JsonProperty("own_pid_path")
    private String ownPidPath;
    
    @JsonProperty("own_parent.title")
    private String ownParentTitle;

    public KrameriusObjectMetadataDto toMetadata() {
        return KrameriusObjectMetadataDto.builder()
                .pid(pid)
                .title(titleSearch)
                .parentPath(ownPidPath)
                .parentTitle(ownParentTitle)
                .build();
    }
}
