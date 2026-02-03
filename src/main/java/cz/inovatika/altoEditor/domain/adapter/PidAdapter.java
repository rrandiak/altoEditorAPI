package cz.inovatika.altoEditor.domain.adapter;

import java.util.UUID;

public class PidAdapter {

    public static UUID toUuid(String pid) {
        if (pid == null) {
            return null;
        }
        if (!pid.startsWith("uuid:")) {
            throw new IllegalArgumentException("PID must start with 'uuid:'");
        }
        return UUID.fromString(pid.substring(5));
    }
}
