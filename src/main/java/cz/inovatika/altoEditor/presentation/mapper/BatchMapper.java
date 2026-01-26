package cz.inovatika.altoEditor.presentation.mapper;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BatchMapper {

    BatchDto toDto(Batch batch);

    Batch toEntity(BatchDto dto);
}