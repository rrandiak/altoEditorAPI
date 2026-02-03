package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.util.List;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KrameriusService {

    private final KrameriusClientFactory clientFactory;

    // TODO: cache clients
    private KrameriusClient getClient(String instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("InstanceId must be provided");
        }
        return clientFactory.getClient(instanceId);
    }

    public boolean hasPermissionToRead(String pid, String instance, String userToken) {
        return getClient(instance).hasPermissionToRead(pid, userToken);
    }

    public KrameriusObjectMetadata getObjectMetadata(String pid, String instance) {
        return getClient(instance).getObjectMetadata(pid);
    }

    public int getChildrenCount(String pid, String instance) {
        return getClient(instance).getChildrenCount(pid);
    }

    public int getPagesCount(String pid, String instance) {
        return getClient(instance).getPagesCount(pid);
    }
    
    public List<KrameriusObjectMetadata> getChildrenMetadata(String pid, String instance) {
        return getClient(instance).getChildrenMetadata(pid);
    }

    public byte[] getFoxmlBytes(String pid, String instance) {
        return getClient(instance).getFoxmlBytes(pid);
    }

    public byte[] getAltoBytes(String pid, String instance) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAltoBytes'");
    }

    public byte[] getImageBytes(String pid, String instance) {
        return getClient(instance).getImageBytes(pid);
    }

    public void uploadAltoOcr(String pid, byte[] alto, byte[] ocr) {
        getClient(instance).uploadAltoOcr(pid, alto, ocr);
    }
}