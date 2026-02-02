package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.UploadAltoOcrResponse;

public interface KrameriusClient {
    
    public KrameriusObjectMetadata getObjectMetadata(String pid, String token);

    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid, String token);

    public int getPagesCount(String pid, String token);

    public int getChildrenCount(String pid, String token);

    public byte[] getImageBytes(String pid, String token);

    public byte[] getFoxmlBytes(String pid, String token);

    public byte[] getAltoBytes(String pid, String token);

    public UploadAltoOcrResponse uploadAltoOcr(String pid, byte[] alto, byte[] ocr, String token);
}
