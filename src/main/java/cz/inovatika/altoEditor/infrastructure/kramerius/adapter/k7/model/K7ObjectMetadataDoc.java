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

    @JsonProperty("level")
    private Integer level;

    @JsonProperty("rels_ext_index.sort")
    private Integer indexInParent;
    
    @JsonProperty("own_parent.pid")
    private String parentPid;

    @JsonProperty("root.pid")
    private String rootPid;

    @JsonProperty("count_page")
    private Integer pagesCount;

    public KrameriusObjectMetadata toMetadata() {
        return KrameriusObjectMetadata.builder()
                .pid(pid)
                .title(title)
                .model(model)
                .level(level)
                .indexInParent(indexInParent)
                .parentPid(parentPid)
                .build();
    }
}
