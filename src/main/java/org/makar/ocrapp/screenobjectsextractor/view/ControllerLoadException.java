package org.makar.ocrapp.screenobjectsextractor.view;

public class ControllerLoadException extends RuntimeException {
    public ControllerLoadException(String fxmlName, Throwable cause) {
        super("Моё первое исключение! Не удалось загрузить FXML: " + fxmlName, cause);
    }
}
