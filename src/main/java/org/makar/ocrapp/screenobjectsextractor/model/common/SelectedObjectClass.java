package org.makar.ocrapp.screenobjectsextractor.model.common;

import javafx.beans.property.*;

import java.util.Objects;

public class SelectedObjectClass {
    private final StringProperty className = new SimpleStringProperty();
    private final IntegerProperty count = new SimpleIntegerProperty(1); // Количество по умолчанию: 1
    private final DoubleProperty minProbability = new SimpleDoubleProperty(0.0);

    public SelectedObjectClass(String className) {
        setClassName(className);
        setCount(1);
        setMinProbability(0.5);
    }

    public SelectedObjectClass(String className, double probability) {
        setClassName(className);
        setMinProbability(probability);
        setMinProbability(0.5);
    }

    public String getClassName() {
        return className.get();
    }

    public StringProperty classNameProperty() {
        return className;
    }

    public int getCount() {
        return count.get();
    }

    public IntegerProperty countProperty() {
        return count;
    }

    public void setCount(int count) {
        if (count >= 1) { // Убедиться, что количество всегда не менее 1
            this.count.set(count);
        } else {
            this.count.set(1); // Если попытка установить <1, сбрасываем до 1
        }
    }

    public void setClassName(String className) {
        this.className.set(className);
    }

    public DoubleProperty minProbabilityProperty() {
        return minProbability;
    }

    public double getMinProbability() {
        return minProbability.get();
    }

    public void setMinProbability(double minProbability) {
        this.minProbability.set(Math.max(0.0, Math.min(1.0, minProbability))); // Гарантируем диапазон 0-1
    }

    public void incrementCount() {
        setCount(getCount() + 1);
    }

    public void decrementCount() {
        setCount(getCount() - 1);
    }

    @Override
    public String toString() {
        return "SelectedObjectClass{" +
                "className='" + className.get() + '\'' +
                ", count=" + count.get() +
                ", minProbability=" + String.format("%.2f", minProbability.get()) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectedObjectClass that = (SelectedObjectClass) o;
        // Сравнение только по имени класса, так как count и minProbability могут меняться для одного и того же "выбранного класса"
        return Objects.equals(className.get(), that.className.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(className.get());
    }
}