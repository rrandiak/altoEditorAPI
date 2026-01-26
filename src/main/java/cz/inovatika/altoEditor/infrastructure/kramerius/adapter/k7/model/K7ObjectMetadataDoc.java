package cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
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

    public KrameriusObjectMetadata toMetadata() {
        return KrameriusObjectMetadata.builder()
                .pid(pid)
                .title(titleSearch)
                .parentPath(ownPidPath)
                .parentTitle(ownParentTitle)
                .build();
    }
}
