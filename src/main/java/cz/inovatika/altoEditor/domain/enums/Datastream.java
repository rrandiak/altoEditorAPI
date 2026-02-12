package cz.inovatika.altoEditor.domain.enums;

public enum Datastream {
    ALTO("text/xml"), TEXT_OCR("text/plain");

    private final String mimeType;

    Datastream(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
