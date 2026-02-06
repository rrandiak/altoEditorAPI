package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionSearchDto;

/** Maps ALTO version entities to DTOs (with content from AltoVersionWithContent). */
@Mapper(componentModel = "spring")
public interface AltoVersionMapper {
    
    @Mapping(target = "pid", expression = "java(digitalObject.getPid())")
    @Mapping(target = "title", source = "pageTitle")
    @Mapping(target = "content", ignore = true)
    AltoVersionDto toDto(AltoVersion digitalObject);

    default AltoVersionDto toDto(AltoVersionWithContent docWithContent) {
        AltoVersionDto dto = toDto(docWithContent.getAltoVersion());
        dto.setContent(docWithContent.getContent());
        return dto;
    }

    AltoVersionSearchDto toSearchDto(AltoVersion searchResult);
}
