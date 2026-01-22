package cz.inovatika.altoEditor.kramerius.domain;

import java.util.List;

import cz.inovatika.altoEditor.core.enums.Role;
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