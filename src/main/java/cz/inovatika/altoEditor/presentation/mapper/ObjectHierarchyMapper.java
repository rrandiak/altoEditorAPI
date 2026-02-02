package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;

@Mapper(componentModel = "spring")
public interface ObjectHierarchyMapper {
    
    HierarchySearchDto toDto(DigitalObject digitalObject);
}
