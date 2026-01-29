package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KrameriusHierarchyNode {

    String pid;
    List<KrameriusHierarchyNode> children;
    String model;
    String title;
    Integer relsExtIndex;
}
