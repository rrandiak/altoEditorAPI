package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.presentation.dto.response.DigitalObjectDto;

@Mapper(componentModel = "spring")
public interface DigitalObjectMapper {
    
    DigitalObjectDto toDto(DigitalObject digitalObject);

    default DigitalObjectDto toDto(DigitalObjectWithContent docWithContent) {
        DigitalObjectDto dto = toDto(docWithContent.getDigitalObject());
        dto.setAltoContent(docWithContent.getContent() != null ? new String(docWithContent.getContent()) : null);
        return dto;
    }

    DigitalObject toEntity(DigitalObjectDto dto);
}
