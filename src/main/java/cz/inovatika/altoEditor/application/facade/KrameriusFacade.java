package cz.inovatika.altoEditor.application.facade;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusHierarchyNode;
import cz.inovatika.altoEditor.presentation.dto.response.ObjectHierarchyNodeDto;
import cz.inovatika.altoEditor.presentation.mapper.ObjectHierarchyMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusFacade {

    private final KrameriusService service;

    private final UserContextService userContext;

    private final KrameriusProperties krameriusConfig;

    private final ObjectHierarchyMapper mapper;

    public ObjectHierarchyNodeDto getHierarchyNode(String pid, String instanceId) {
        KrameriusHierarchyNode node = service.getHierarchyNode(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());

        return mapper.toDto(node);
    }
}
