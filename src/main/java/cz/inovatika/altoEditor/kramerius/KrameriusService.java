package cz.inovatika.altoEditor.kramerius;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.kramerius.domain.KrameriusObjectMetadataDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KrameriusService {

    private final KrameriusClientFactory clientFactory;

    private KrameriusClient getClient(String instanceId) {
        return clientFactory.getClient(instanceId);
    }

    public KrameriusObjectMetadataDto getObjectMetadata(String pid, String instanceId, String token) {
        return getClient(instanceId).getObjectMetadata(pid, token);
    }

    public byte[] getFoxmlBytes(String pid, String instanceId, String token) {
        return getClient(instanceId).getFoxmlBytes(pid, token);
    }

    public byte[] getImageBytes(String pid, String instanceId, String token) {
        return getClient(instanceId).getImageBytes(pid, token);
    }

    public void uploadAltoOcr(String pid, String instanceId, String alto, String ocr, String token) {
        getClient(instanceId).uploadAltoOcr(pid, alto, ocr, token);
    }
}