package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionSearchDto;

@Mapper(componentModel = "spring")
public interface AltoVersionMapper {
    
    @Mapping(target = "pid", expression = "java(digitalObject.getPid())")
    AltoVersionDto toDto(AltoVersion digitalObject);

    default AltoVersionDto toDto(AltoVersionWithContent docWithContent) {
        AltoVersionDto dto = toDto(docWithContent.getAltoVersion());
        dto.setAltoContent(docWithContent.getContent() != null ? new String(docWithContent.getContent()) : null);
        return dto;
    }

    AltoVersion toEntity(AltoVersionDto dto);

    AltoVersionSearchDto toSearchDto(AltoVersion searchResult);
}
