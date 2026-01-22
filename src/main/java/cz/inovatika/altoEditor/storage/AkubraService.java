package cz.inovatika.altoEditor.storage;

import org.springframework.stereotype.Service;

import cz.inovatika.altoEditor.core.enums.Datastream;

@Service
public class AkubraService {
    
    public void saveFoxml(String pid, byte[] foxml) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveFoxml'");
    }

    private void saveDatastreamContent(String pid, Datastream ds, String version, byte[] bytes) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveDatastreamContent'");
    }

    public void saveAltoContent(String pid, String version, byte[] bytes) {
        // TODO: Check if its ALTO datastream
        saveDatastreamContent(pid, Datastream.ALTO, version, bytes);
    }

    public void saveOcrContent(String pid, String version, byte[] bytes) {
        saveDatastreamContent(pid, Datastream.OCR, version, bytes);
    }

    public String getDsVersion(String pid, Datastream alto) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDsVersion'");
    }

    public String getDatastreamContent(String pid, Datastream datastream, String version) {
        // Implementation to retrieve datastream content from Akubra storage
        // TODO:
        return ""; // Placeholder return
    }
}
