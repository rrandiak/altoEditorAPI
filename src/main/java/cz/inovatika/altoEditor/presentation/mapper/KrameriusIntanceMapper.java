package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusInstance;

@Mapper(componentModel = "spring")
public interface KrameriusIntanceMapper {

    KrameriusInstance toDto(String code, KrameriusProperties.KrameriusInstance config);
}
