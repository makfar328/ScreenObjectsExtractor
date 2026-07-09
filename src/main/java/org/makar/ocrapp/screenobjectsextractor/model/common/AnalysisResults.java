package org.makar.ocrapp.screenobjectsextractor.model.common;

import ai.djl.modality.cv.output.DetectedObjects;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;

public class AnalysisResults {
    private TextObjects textObjects;
    private DetectedObjects detectedObjects;

    public AnalysisResults() {
        textObjects = null;
        detectedObjects = null;
    }

    public AnalysisResults(TextObjects textObjects, DetectedObjects detectedObjects) {
        this.textObjects = textObjects;
        this.detectedObjects = detectedObjects;
    }

    public TextObjects getTextObjects() {
        return textObjects;
    }

    public DetectedObjects getDetectedObjects() {
        return detectedObjects;
    }

    public void setTextObjects(TextObjects textObjects) {
        this.textObjects = textObjects;
    }

    public void setDetectedObjects(DetectedObjects detectedObjects) {
        this.detectedObjects = detectedObjects;
    }
}
