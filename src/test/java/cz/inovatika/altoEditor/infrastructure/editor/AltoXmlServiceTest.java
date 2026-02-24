package cz.inovatika.altoEditor.infrastructure.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Unit tests for {@link AltoXmlService}, in particular
 * {@link AltoXmlService#convertAltoToOcr(byte[])}.
 */
class AltoXmlServiceTest {

    private AltoXmlService altoXmlService;

    private static final String SAMPLE_ALTO = """
            <alto xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.loc.gov/standards/alto/ns-v2#">
              <Description>
                <MeasurementUnit>pixel</MeasurementUnit>
                <sourceImageInformation>
                  <fileName>3b8f0f47-833e-421e-9e80-c2e366bc90cc</fileName>
                </sourceImageInformation>
                <OCRProcessing ID="IdOcr">
                  <ocrProcessingStep>
                    <processingDateTime>2026-02-24T09:40:57.985648</processingDateTime>
                    <processingSoftware>
                      <softwareCreator>Project PERO</softwareCreator>
                      <softwareName>czech_old_printed</softwareName>
                      <softwareVersion>2022-09-22</softwareVersion>
                    </processingSoftware>
                  </ocrProcessingStep>
                </OCRProcessing>
              </Description>
              <Layout>
                <Page ID="id_3b8f0f47-833e-421e-9e80-c2e366bc90cc" PHYSICAL_IMG_NR="1" HEIGHT="2294" WIDTH="1500">
                  <TopMargin HEIGHT="83" WIDTH="1500" VPOS="0" HPOS="0"/>
                  <LeftMargin HEIGHT="2294" WIDTH="76" VPOS="0" HPOS="0"/>
                  <RightMargin HEIGHT="2294" WIDTH="0" VPOS="0" HPOS="1500"/>
                  <BottomMargin HEIGHT="0" WIDTH="1500" VPOS="2294" HPOS="0"/>
                  <PrintSpace HEIGHT="2210" WIDTH="1423" VPOS="83" HPOS="76">
                    <TextBlock ID="block_r000" HEIGHT="895" WIDTH="447" VPOS="112" HPOS="76">
                      <TextLine ID="line_r000-l003" BASELINE="128" VPOS="112" HPOS="197" HEIGHT="22" WIDTH="326">
                        <String CONTENT="pravdu" HEIGHT="19" WIDTH="53" VPOS="111" HPOS="201" WC="1"/>
                        <SP WIDTH="4" VPOS="111" HPOS="255"/>
                        <String CONTENT="nejvíce" HEIGHT="19" WIDTH="53" VPOS="111" HPOS="265" WC="1"/>
                        <SP WIDTH="4" VPOS="111" HPOS="319"/>
                        <String CONTENT="kněvají." HEIGHT="20" WIDTH="60" VPOS="111" HPOS="329" WC="1"/>
                        <SP WIDTH="4" VPOS="111" HPOS="390"/>
                        <String CONTENT="Staré" HEIGHT="20" WIDTH="38" VPOS="112" HPOS="398" WC="1"/>
                        <SP WIDTH="4" VPOS="112" HPOS="437"/>
                        <String CONTENT="přísloví" HEIGHT="20" WIDTH="57" VPOS="112" HPOS="448" WC="1"/>
                        <SP WIDTH="4" VPOS="112" HPOS="506"/>
                        <String CONTENT="a" HEIGHT="19" WIDTH="9" VPOS="114" HPOS="512" WC="1"/>
                      </TextLine>
                      <TextLine ID="line_r000-l006" BASELINE="147" VPOS="130" HPOS="181" HEIGHT="23" WIDTH="341">
                        <String CONTENT="at" HEIGHT="19" WIDTH="13" VPOS="130" HPOS="180" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="194"/>
                        <String CONTENT="ze" HEIGHT="19" WIDTH="16" VPOS="130" HPOS="202" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="219"/>
                        <String CONTENT="lži." HEIGHT="19" WIDTH="28" VPOS="130" HPOS="224" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="253"/>
                        <String CONTENT="Jak" HEIGHT="19" WIDTH="25" VPOS="130" HPOS="271" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="297"/>
                        <String CONTENT="to" HEIGHT="19" WIDTH="16" VPOS="130" HPOS="303" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="319"/>
                        <String CONTENT="navléci," HEIGHT="20" WIDTH="60" VPOS="130" HPOS="330" WC="1"/>
                        <SP WIDTH="4" VPOS="130" HPOS="390"/>
                        <String CONTENT="aby" HEIGHT="19" WIDTH="28" VPOS="131" HPOS="400" WC="1"/>
                        <SP WIDTH="4" VPOS="131" HPOS="429"/>
                        <String CONTENT="se" HEIGHT="19" WIDTH="16" VPOS="132" HPOS="437" WC="1"/>
                        <SP WIDTH="4" VPOS="132" HPOS="453"/>
                        <String CONTENT="vlk" HEIGHT="19" WIDTH="23" VPOS="132" HPOS="464" WC="1"/>
                        <SP WIDTH="4" VPOS="132" HPOS="487"/>
                        <String CONTENT="na-" HEIGHT="19" WIDTH="23" VPOS="133" HPOS="498" WC="1" SUBS_CONTENT="naa." SUBS_TYPE="HypPart1"/>
                      </TextLine>
                      <TextLine ID="line_r000-l009" BASELINE="164" VPOS="148" HPOS="77" HEIGHT="17" WIDTH="16">
                        <String CONTENT="a." HEIGHT="20" WIDTH="16" VPOS="148" HPOS="77" WC="1.0" SUBS_CONTENT="naa." SUBS_TYPE="HypPart2"/>
                      </TextLine>
                    </TextBlock>
                  </PrintSpace>
                </Page>
              </Layout>
            </alto>
            """;

    @BeforeEach
    void setUp() throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        XmlLSResolver resolver = new XmlLSResolver();
        altoXmlService = new AltoXmlService(xmlMapper, resolver);
    }

    @Test
    @DisplayName("convertAltoToOcr extracts text from PrintSpace TextBlocks and TextLines")
    void convertAltoToOcr_extractsOcrText() {
        byte[] altoBytes = SAMPLE_ALTO.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String ocr = altoXmlService.convertAltoToOcr(altoBytes);

        String line1 = "pravdu nejvíce kněvají. Staré přísloví a";
        String line2 = "at ze lži. Jak to navléci, aby se vlk na-";
        String line3 = "a.";
        String expected = line1 + "\n" + line2 + "\n" + line3;
        assertThat(ocr).isEqualTo(expected);
    }
}
