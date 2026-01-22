package cz.inovatika.altoEditor.api.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.api.dto.DigitalObjectAltoDto;
import cz.inovatika.altoEditor.api.dto.DigitalObjectDto;
import cz.inovatika.altoEditor.core.entity.DigitalObject;
import cz.inovatika.altoEditor.core.service.container.DigitalObjectWithContent;

@Mapper(componentModel = "spring")
public interface DigitalObjectMapper {
    
    DigitalObjectDto toDto(DigitalObject digitalObject);

    DigitalObject toEntity(DigitalObjectDto dto);

    DigitalObjectAltoDto toAltoDto(DigitalObjectWithContent digitalObjectWithContent);
}
