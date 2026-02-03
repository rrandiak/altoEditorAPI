package cz.inovatika.altoEditor.domain.enums;

public enum Model {
    PAGE("page");

    private final String modelName;

    Model(String modelName) {
        this.modelName = modelName;
    }

    public boolean isModel(String modelName) {
        return this.modelName.equals(modelName);
    }
    
    public String getModelName() {
        return modelName;
    }
}
