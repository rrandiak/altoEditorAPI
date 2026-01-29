package cz.inovatika.altoEditor.presentation.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectHierarchyNodeDto {

    String pid;
    List<ObjectHierarchyNodeDto> children;
    String model;
    String title;
    Short level;
    Short relsExtIndex;

    List<Integer> versions;
}
