package cz.inovatika.altoEditor.kramerius;

import cz.inovatika.altoEditor.kramerius.domain.KrameriusObjectMetadataDto;
import cz.inovatika.altoEditor.kramerius.domain.UploadAltoOcrResponse;

public interface KrameriusClient {
    
    public KrameriusObjectMetadataDto getObjectMetadata(String pid, String token);

    public byte[] getImageBytes(String pid, String token);

    public byte[] getFoxmlBytes(String pid, String token);

    public byte[] getAltoBytes(String pid, String token);

    public UploadAltoOcrResponse uploadAltoOcr(String pid, String alto, String ocr, String token);
}
