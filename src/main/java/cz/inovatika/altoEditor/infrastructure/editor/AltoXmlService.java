package cz.inovatika.altoEditor.infrastructure.editor;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;


@Service
public class AltoXmlService {

    public static final String ALTO_ID = "ALTO";
    public static final String ALTO_LABEL = "ALTO for this object";
    public static final String ALTO_FORMAT_URI = "http://www.loc.gov/standards/alto/ns-v2#";

    private static final String[] ALTO_SCHEMA_PATHS = {
            "/xsd/alto-v2.0.xsd",
            "/xsd/alto-v2.1.xsd",
            "/xsd/alto-v3.0.xsd"
    };

    private final XmlMapper xmlMapper;
    private final List<Schema> schemas;

    public AltoXmlService(XmlMapper xmlMapper, XmlLSResolver resolver) throws SAXException {
        this.xmlMapper = xmlMapper;
        this.schemas = loadSchemas(resolver);
    }

    /**
     * Parses ALTO XML and extracts TEXT_OCR text.
     *
     * @param alto the ALTO XML content
     * @return extracted text
     */
    public String convertAltoToOcr(byte[] alto) {
        try {
            JsonNode root = xmlMapper.readTree(alto);
            JsonNode printSpace = root.path("alto")
                    .path("Layout")
                    .path("Page")
                    .path("PrintSpace");

            return processPrintSpace(printSpace).trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse ALTO XML", e);
        }
    }

    private String processPrintSpace(JsonNode printSpaceNode) {
        StringBuilder sb = new StringBuilder();

        Iterator<JsonNode> textBlocks = getAsArray(printSpaceNode, "TextBlock");
        while (textBlocks.hasNext()) {
            String blockText = processTextBlock(textBlocks.next());
            if (!blockText.isBlank()) {
                sb.append(blockText).append("\n\n");
            }
        }

        return sb.toString().replaceAll("\n\n+", "\n\n");
    }

    private String processTextBlock(JsonNode textBlockNode) {
        StringBuilder sb = new StringBuilder();

        Iterator<JsonNode> textLines = getAsArray(textBlockNode, "TextLine");
        while (textLines.hasNext()) {
            String lineText = processTextLine(textLines.next());
            if (!lineText.isBlank()) {
                sb.append(lineText).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String processTextLine(JsonNode textLineNode) {
        StringBuilder sb = new StringBuilder();

        Iterator<JsonNode> strings = getAsArray(textLineNode, "String");
        while (strings.hasNext()) {
            JsonNode stringNode = strings.next();
            String content = transformValue(stringNode.path("CONTENT"));
            if (content != null && !content.isBlank()) {
                sb.append(content).append(" ");
            }
        }

        return sb.toString().trim();
    }

    private Iterator<JsonNode> getAsArray(JsonNode parent, String fieldName) {
        JsonNode node = parent.path(fieldName);
        if (node.isArray()) {
            return node.elements();
        } else if (!node.isMissingNode()) {
            return java.util.Collections.singletonList(node).iterator();
        } else {
            return java.util.Collections.emptyIterator();
        }
    }

    private String transformValue(JsonNode valueNode) {
        if (valueNode.isTextual()) {
            return valueNode.asText();
        } else if (valueNode.isNumber()) {
            return valueNode.asText();
        } else if (!valueNode.isMissingNode() && !valueNode.isNull()) {
            return valueNode.toString();
        }
        return null;
    }

    public boolean isAlto(String altoXml) throws SAXException, IOException {
        List<SAXException> errors = new ArrayList<>();

        for (Schema schema : schemas) {
            try {
                Validator validator = schema.newValidator();
                validator.validate(new StreamSource(new StringReader(altoXml)));
                return true;
            } catch (SAXException ex) {
                errors.add(ex);
            }
        }

        SAXException combined = new SAXException("XML is not valid ALTO (checked " + schemas.size() + " schemas)");
        errors.forEach(combined::addSuppressed);

        throw combined;
    }

    public String nextVersion(String version) {
        String[] parts = version.split("\\.", 2);
        int revision = Integer.parseInt(parts[1]);
        return parts[0] + "." + (revision + 1);
    }

    private static List<Schema> loadSchemas(XmlLSResolver resolver) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(resolver);

        List<Schema> result = new ArrayList<>();
        for (String path : ALTO_SCHEMA_PATHS) {
            result.add(factory.newSchema(
                    AltoXmlService.class.getResource(path)));
        }
        return Collections.unmodifiableList(result);
    }
}
