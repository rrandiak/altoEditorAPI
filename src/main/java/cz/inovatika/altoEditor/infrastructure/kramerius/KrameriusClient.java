package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;

public interface KrameriusClient {

    public KrameriusUser getUser(String userToken);

    public boolean hasPermissionToRead(String pid, String userToken);

    public KrameriusObjectMetadata getObjectMetadata(String pid);

    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid);

    public int getPagesCount(String pid);

    public int getChildrenCount(String pid);

    public byte[] getImageBytes(String pid);

    public byte[] getAltoBytes(String pid);

    public void uploadAltoOcr(String pid, byte[] alto, byte[] ocr);

    public void planObjectIndexing(String pid);

    public void planObjectIndexing(List<String> pids);

    public void planHierarchyIndexing(String pid);
}
