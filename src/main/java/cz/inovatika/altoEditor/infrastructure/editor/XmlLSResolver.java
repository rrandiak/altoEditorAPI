package cz.inovatika.altoEditor.infrastructure.editor;

import java.io.InputStream;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

@Component
public class XmlLSResolver implements LSResourceResolver {

    private final DOMImplementationLS dls;

    private static final Map<String, String> URL_MAP = Map.of(
            "http://www.w3.org/2001/03/xml.xsd", "/xsd/xml.xsd",
            "http://www.loc.gov/standards/xlink/xlink.xsd", "/xsd/xlink.xsd",
            "http://www.loc.gov/mods/xml.xsd", "/xsd/xml.xsd");

    public XmlLSResolver() {
        try {
            dls = (DOMImplementationLS) DOMImplementationRegistry.newInstance()
                    .getDOMImplementation("LS");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize DOMImplementationLS", e);
        }
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId,
            String systemId, String baseURI) {
        String location = URL_MAP.get(systemId);
        if (location == null)
            throw new IllegalStateException("No mapping for systemId: " + systemId);

        InputStream is = getClass().getResourceAsStream(location);
        LSInput input = dls.createLSInput();
        input.setByteStream(is);
        input.setPublicId(publicId);
        input.setSystemId(systemId);
        return input;
    }
}