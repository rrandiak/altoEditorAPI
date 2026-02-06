package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;

/** Maps DigitalObject to HierarchySearchDto (pagesCount/pagesWithAlto set by facade). */
@Mapper(componentModel = "spring")
public interface ObjectHierarchyMapper {

    @Mapping(target = "pagesCount", ignore = true)
    @Mapping(target = "pagesWithAlto", ignore = true)
    HierarchySearchDto toSearchDto(DigitalObject digitalObject);
}
