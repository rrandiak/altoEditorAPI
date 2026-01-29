package cz.inovatika.altoEditor.domain.enums;

public enum Model {
    PAGE("page");

    private final String modelName;

    Model(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }
}
