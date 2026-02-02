package cz.inovatika.altoEditor.presentation.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusDigitalObjectDto;
import cz.inovatika.altoEditor.presentation.mapper.KrameriusDigitalObjectMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusFacade {

    private final KrameriusService service;

    private final KrameriusDigitalObjectMapper mapper;

    private final UserContextService userContext;

    private final KrameriusProperties krameriusConfig;

    public KrameriusDigitalObjectDto getObjectMetadata(String pid, String instanceId) {
        KrameriusObjectMetadata metadata = service.getObjectMetadata(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());
        int childrenCount = service.getChildrenCount(pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(), userContext.getToken());
        int pagesCount = service.getPagesCount(pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(), userContext.getToken());

        return mapper.toDto(metadata, childrenCount, pagesCount);
    }

    public List<KrameriusDigitalObjectDto> getChildrenMetadata(String pid, String instanceId) {
        List<KrameriusObjectMetadata> childrenMetadata = service.getChildrenMetadata(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());

        return childrenMetadata.stream()
                .map(metadata -> {
                    int childrenCount = service.getChildrenCount(metadata.getPid(),
                            instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                            userContext.getToken());
                    int pagesCount = service.getPagesCount(metadata.getPid(),
                            instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                            userContext.getToken());
                    return mapper.toDto(metadata, childrenCount, pagesCount);
                })
                .toList();
    }
}
