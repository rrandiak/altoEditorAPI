package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.service.EngineService;
import cz.inovatika.altoEditor.presentation.dto.request.EngineSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.EngineDto;
import cz.inovatika.altoEditor.presentation.mapper.EngineMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EngineFacade {

    private final EngineService service;

    private final EngineMapper mapper;

    public Page<EngineDto> searchEngines(EngineSearchRequest request, Pageable pageable) {
        return service.searchEngines(request.getEnabled(), pageable).map(mapper::toDto);
    }

}
