package cz.inovatika.altoEditor.domain.enums;

import java.util.Set;
import java.util.stream.Collectors;

public enum Model {
    PAGE("page"),
    PERIODICALITEM("periodicalitem"),
    MONOGRAPH("monograph"),
    ARTICLE("article"),
    SUPPLEMENT("supplement"),
    PERIODICALVOLUME("periodicalvolume"),
    GRAPHIC("graphic"),
    MAP("map"),
    MONOGRAPHUNIT("monographunit"),
    SHEETMUSIC("sheetmusic"),
    TRACK("track"),
    PERIODICAL("periodical"),
    SOUNDUNIT("soundunit"),
    INTERNALPART("internalpart"),
    COLLECTION("collection"),
    SOUNDRECORDING("soundrecording"),
    ARCHIVE("archive"),
    CONVOLUTE("convolute"),
    MANUSCRIPT("manuscript"),
    PICTURE("picture");

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

    public static Model fromModelName(String modelName) {
        if (modelName == null) {
            return null;
        }
        for (Model m : values()) {
            if (m.modelName.equals(modelName)) {
                return m;
            }
        }
        return null;
    }

    public static final Set<Model> SHOULD_IGNORE_FOR_HIERARCHY_RETRIEVAL = Set.of(ARTICLE, TRACK, INTERNALPART);

    public boolean shouldIgnoreForHierarchyRetrieval() {
        return SHOULD_IGNORE_FOR_HIERARCHY_RETRIEVAL.contains(this);
    }

    public static String getShouldIgnoreQueryPart() {
        return "-model:(" + SHOULD_IGNORE_FOR_HIERARCHY_RETRIEVAL.stream().map(Model::getModelName)
                .collect(Collectors.joining(" OR ")) + ")";
    }
}
