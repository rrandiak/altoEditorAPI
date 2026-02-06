package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusDigitalObjectDto;

/** Maps Kramerius metadata + counts to KrameriusDigitalObjectDto. */
@Mapper(componentModel = "spring")
public interface KrameriusDigitalObjectMapper {
    
    KrameriusDigitalObjectDto toDto(KrameriusObjectMetadata metadata, int childrenCount, int pagesCount);
}
