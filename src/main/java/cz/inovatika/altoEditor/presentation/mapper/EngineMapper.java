package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.service.container.Engine;
import cz.inovatika.altoEditor.presentation.dto.response.EngineDto;

@Mapper(componentModel = "spring")
public interface EngineMapper {
    
    EngineDto toDto(Engine engine);
}
