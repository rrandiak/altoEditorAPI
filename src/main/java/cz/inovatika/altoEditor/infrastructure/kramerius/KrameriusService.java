package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KrameriusService {

    private final KrameriusClientFactory clientFactory;

    private KrameriusClient getClient(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("InstanceId must be provided");
        }
        return clientFactory.getClient(instanceId);
    }

    public KrameriusObjectMetadata getObjectMetadata(String pid, String instanceId, String token) {
        return getClient(instanceId).getObjectMetadata(pid, token);
    }

    public int getChildrenCount(String pid, String instanceId, String token) {
        return getClient(instanceId).getChildrenCount(pid, token);
    }

    public int getPagesCount(String pid, String instanceId, String token) {
        return getClient(instanceId).getPagesCount(pid, token);
    }
    
    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid, String instanceId, String token) {
        return getClient(instanceId).getChildrenMetadata(pid, token);
    }

    public byte[] getFoxmlBytes(String pid, String instanceId, String token) {
        return getClient(instanceId).getFoxmlBytes(pid, token);
    }

    public byte[] getImageBytes(String pid, String instanceId, String token) {
        return getClient(instanceId).getImageBytes(pid, token);
    }

    public void uploadAltoOcr(String pid, String instanceId, byte[] alto, byte[] ocr, String token) {
        getClient(instanceId).uploadAltoOcr(pid, alto, ocr, token);
    }
}