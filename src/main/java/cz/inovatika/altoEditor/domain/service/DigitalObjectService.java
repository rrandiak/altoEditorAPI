package cz.inovatika.altoEditor.domain.service;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.DigitalObjectRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DigitalObjectService {
    
    private final DigitalObjectRepository digitalObjectRepository;

    public DigitalObject createFromKrameriusMetadata(KrameriusObjectMetadata metadata, DigitalObject parent) {
        return digitalObjectRepository.save(DigitalObject.builder()
        .pid(metadata.getPid())
        .model(metadata.getModel())
        .title(metadata.getTitle())
        .level(metadata.getLevel())
        .indexInParent(metadata.getIndexInParent())
        .parent(parent)
        .build());
    }
}
