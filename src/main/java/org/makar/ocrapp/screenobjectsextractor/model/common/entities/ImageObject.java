package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import java.awt.image.BufferedImage;

public class ImageObject {
    private int x1, y1, x2, y2;
    private BufferedImage image;
    private String confidence;
    private int classId;
    private String className;

    public ImageObject(int x1, int y1, int x2, int y2, BufferedImage image, String confidence, int classId) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.image = image;
        this.confidence = confidence;
        this.classId = classId;
    }
    public int getX1() {
        return x1;
    }
    public int getY1() {
        return y1;
    }
    public int getX2() {
        return x2;
    }
    public int getY2() {
        return y2;
    }
    public BufferedImage getImage() {
        return image;
    }
    public String getConfidence() {
        return confidence;
    }
    public int getClassId() {
        return classId;
    }
    public String getClassName() {
        return className;
    }


}
