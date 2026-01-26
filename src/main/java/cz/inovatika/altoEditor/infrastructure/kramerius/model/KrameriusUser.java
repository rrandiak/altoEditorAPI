package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KrameriusUser {

    private final String username;
    private final List<Role> roles;

    private final boolean editor;
    private final boolean curator;
}