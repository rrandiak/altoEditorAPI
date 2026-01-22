package cz.inovatika.altoEditor.kramerius.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.ApplicationConfig;
import cz.inovatika.altoEditor.core.enums.Role;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusUserFactory {

    private final ApplicationConfig config;

    public KrameriusUser from(String username, List<String> roleNames) {

        Map<String, Role> roleMapping = Map.of(
            config.getPermission().getEditor(), Role.EDITOR,
            config.getPermission().getCurator(), Role.CURATOR
        );

        List<Role> roles = roleNames.stream()
                .map(roleMapping::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return KrameriusUser.builder()
                .username(username)
                .roles(roles)
                .editor(roles.contains(Role.EDITOR))
                .curator(roles.contains(Role.CURATOR))
                .build();
    }
}