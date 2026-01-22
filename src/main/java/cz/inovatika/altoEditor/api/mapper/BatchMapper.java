package cz.inovatika.altoEditor.api.mapper;

import cz.inovatika.altoEditor.api.dto.BatchDto;
import cz.inovatika.altoEditor.core.entity.Batch;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BatchMapper {

    BatchDto toDto(Batch batch);

    Batch toEntity(BatchDto dto);
}