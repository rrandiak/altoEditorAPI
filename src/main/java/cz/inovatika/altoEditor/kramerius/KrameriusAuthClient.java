package cz.inovatika.altoEditor.kramerius;

import cz.inovatika.altoEditor.kramerius.domain.KrameriusUser;

public interface KrameriusAuthClient {

    public KrameriusUser getUser(String token);

}
