package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;

/** Maps Batch entity to BatchDto. */
@Mapper(componentModel = "spring")
public interface BatchMapper {

    BatchDto toDto(Batch batch);
}