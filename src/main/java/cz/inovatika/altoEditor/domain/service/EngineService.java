package cz.inovatika.altoEditor.domain.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.container.Engine;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EngineService {

    private final UserRepository userRepository;

    private final EnginesProperties enginesProperties;

    public Page<Engine> searchEngines(Boolean enabled, Pageable pageable) {
        List<Engine> allEngines = userRepository.findAllIsEngine().stream()
            .map(user -> new Engine(
                user.getUsername(),
                enginesProperties.getEngines().containsKey(user.getUsername())
            ))
            .filter(engine -> enabled == null || engine.isEnabled() == enabled)
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allEngines.size());
        List<Engine> pageContent = start > end ? List.<Engine>of() : allEngines.subList(start, end);

        return new PageImpl<>(pageContent, pageable, allEngines.size());
    }

    public boolean isEngineEnabled(String engine) {
        return enginesProperties.getEngines().containsKey(engine);
    }
}
