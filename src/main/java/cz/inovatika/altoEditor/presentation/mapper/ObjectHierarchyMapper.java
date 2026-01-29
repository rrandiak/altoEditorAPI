package cz.inovatika.altoEditor.presentation.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.model.ObjectHierarchyNode;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusHierarchyNode;
import cz.inovatika.altoEditor.presentation.dto.response.ObjectHierarchyNodeDto;

@Mapper(componentModel = "spring")
public interface ObjectHierarchyMapper {

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "versions", source = "digitalObjects", qualifiedByName = "extractVersions")
    ObjectHierarchyNodeDto toDto(KrameriusHierarchyNode node);

    @Mapping(target = "pid", expression = "java(node.getPid())")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "versions", source = "digitalObjects", qualifiedByName = "extractVersions")
    ObjectHierarchyNodeDto toDto(ObjectHierarchyNode node);

    @Mapping(target = "pid", expression = "java(node.getPid())")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "versions", source = "nodeDigitalObjects", qualifiedByName = "extractVersions")
    ObjectHierarchyNodeDto toDto(ObjectHierarchyNode node, List<DigitalObject> nodeDigitalObjects);

    @Named("extractVersions")
    default List<Integer> extractVersions(List<DigitalObject> digitalObjects) {
        if (digitalObjects == null || digitalObjects.isEmpty()) {
            return null;
        }
        return digitalObjects.stream()
                .map(DigitalObject::getVersion)
                .toList();
    }
}
