package org.makar.ocrapp.screenobjectsextractor.model.common;

import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection.YoloPostProcessor;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Класс для управления списком всех доступных классов объектов,
 * распознаваемых моделью YOLO, а также общей опцией "Any Object Class".
 * Предоставляет неизменяемый список классов.
 */
public class ObjectsClassList {
    private final List<String> classNames;

    public ObjectsClassList() {
        List<String> tempClassNames = new ArrayList<>();
        tempClassNames.add("Any object class");
        tempClassNames.addAll(YoloPostProcessor.getClassNames()); // тут
        this.classNames = Collections.unmodifiableList(tempClassNames);
    }

    public List<String> getClasses() {
        return classNames;
    }

    public String index(int i) {
        return classNames.get(i);
    }
}
