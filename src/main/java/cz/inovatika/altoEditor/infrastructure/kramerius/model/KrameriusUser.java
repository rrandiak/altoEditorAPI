package cz.inovatika.altoEditor.infrastructure.kramerius.model;

import java.util.List;

import cz.inovatika.altoEditor.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KrameriusUser {

    private final String uid;
    private final String username;
    private final List<Role> roles;
    private String instance;

    private final boolean editor;
    private final boolean curator;
}