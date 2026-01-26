package cz.inovatika.altoEditor.domain.enums;

import lombok.Getter;

@Getter
public enum SpecialUser {
    ALTOEDITOR("altoeditor"),
    PERO("pero");

    private final String username;

    SpecialUser(String username) {
        this.username = username;
    }
}
