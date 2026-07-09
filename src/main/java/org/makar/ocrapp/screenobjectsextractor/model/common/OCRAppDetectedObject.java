package org.makar.ocrapp.screenobjectsextractor.model.common;


/* свой объект не зависит от сторонней библиотеки -- ниже сцепление
легче сохранить в базу данных
содержит меньше данных (только нужные) и наоборот, можно расширить
 */
// org.makar.ocrapp.screenobjectsextractor.model.common/OCRAppDetectedObject.java

import ai.djl.modality.cv.output.DetectedObjects;

import java.util.Objects;

public class OCRAppDetectedObject {
    private final String className;
    private final double probability; // Вероятность того, что это объект класса (0.0 - 1.0)
    private final double boxX;
    private final double boxY;
    private final double boxWidth;
    private final double boxHeight;


    public OCRAppDetectedObject(String className, double probability,
                                double boxX, double boxY,
                                double boxWidth, double boxHeight) {
        this.className = className;
        this.probability = probability;
        this.boxX = boxX;
        this.boxY = boxY;
        this.boxWidth = boxWidth;
        this.boxHeight = boxHeight;
    }

    /** Без bounding box — для записей из БД где координаты не хранятся */
    public OCRAppDetectedObject(String className, double probability) {
        this(className, probability, 0, 0, 0, 0);
    }

    /** Конвертирующий из DJL — единственное место где DJL проникает в доменный объект */
    public OCRAppDetectedObject(DetectedObjects.DetectedObject detectedObject) {
        this(
                detectedObject.getClassName(),
                detectedObject.getProbability(),
                detectedObject.getBoundingBox().getBounds().getX(),
                detectedObject.getBoundingBox().getBounds().getY(),
                detectedObject.getBoundingBox().getBounds().getWidth(),
                detectedObject.getBoundingBox().getBounds().getHeight()
        );
    }

    public String getClassName() {
        return className;
    }
    public double getProbability() {
        return probability;
    }
    public double getBoxX()      { return boxX; }
    public double getBoxY()      { return boxY; }
    public double getBoxWidth()  { return boxWidth; }
    public double getBoxHeight() { return boxHeight; }

    @Override
    public String toString() {
        return "OCRAppDetectedObject{" +
                "className='" + className + '\'' +
                ", probability=" + String.format("%.2f", probability) +
                ", box=[x=" + String.format("%.3f", boxX) +
                ", y=" + String.format("%.3f", boxY) +
                ", w=" + String.format("%.3f", boxWidth) +
                ", h=" + String.format("%.3f", boxHeight) + "]" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OCRAppDetectedObject that = (OCRAppDetectedObject) o;
        return Double.compare(that.probability, probability) == 0
                && Double.compare(that.boxX, boxX) == 0
                && Double.compare(that.boxY, boxY) == 0
                && Double.compare(that.boxWidth, boxWidth) == 0
                && Double.compare(that.boxHeight, boxHeight) == 0
                && Objects.equals(className, that.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, probability, boxX, boxY, boxWidth, boxHeight);
    }
}