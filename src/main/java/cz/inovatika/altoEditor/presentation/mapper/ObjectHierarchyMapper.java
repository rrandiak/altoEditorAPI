package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;

/** Maps DigitalObject to HierarchySearchDto (pagesCount/pagesWithAlto from entity). */
@Mapper(componentModel = "spring")
public interface ObjectHierarchyMapper {

    HierarchySearchDto toSearchDto(DigitalObject digitalObject);
}
