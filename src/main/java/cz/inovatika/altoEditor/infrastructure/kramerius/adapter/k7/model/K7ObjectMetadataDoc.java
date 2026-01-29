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

    @JsonProperty("model")
    private String model;
    
    @JsonProperty("title.search")
    private String title;

    @JsonProperty("rels_ext_index.sort")
    private Integer indexInParent;
    
    @JsonProperty("own_parent.pid")
    private String parentPid;

    @JsonProperty("own_parent.model")
    private String parentModel;
    
    @JsonProperty("own_parent.title")
    private String parentTitle;

    @JsonProperty("root.title")
    private String rootTitle;

    public KrameriusObjectMetadata toMetadata() {
        return KrameriusObjectMetadata.builder()
                .pid(pid)
                .title(titleSearch)
                .parentPath(ownPidPath)
                .parentTitle(ownParentTitle)
                .build();
    }
}
