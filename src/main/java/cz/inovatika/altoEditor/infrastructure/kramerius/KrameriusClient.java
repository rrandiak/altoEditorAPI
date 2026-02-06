package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.UploadAltoOcrResponse;

public interface KrameriusClient {

    public KrameriusUser getUser(String userToken);

    public boolean hasPermissionToRead(String pid, String userToken);

    public KrameriusObjectMetadata getObjectMetadata(String pid);

    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid);

    public int getPagesCount(String pid);

    public int getChildrenCount(String pid);

    public byte[] getImageBytes(String pid);

    public byte[] getAltoBytes(String pid);

    public UploadAltoOcrResponse uploadAltoOcr(String pid, byte[] alto, byte[] ocr);
}
