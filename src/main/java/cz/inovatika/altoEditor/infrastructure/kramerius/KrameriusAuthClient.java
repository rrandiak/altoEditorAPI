package cz.inovatika.altoEditor.infrastructure.kramerius;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;

public interface KrameriusAuthClient {

    public KrameriusUser getUser(String token);

}
