package org.makar.ocrapp.screenobjectsextractor.view.main.components;

import javafx.scene.control.TextArea;

public class TextAreaAutoExpander {
    public static void apply(TextArea textArea, int expandedRows, int collapsedRows) {
        textArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            textArea.setPrefRowCount(isFocused ? expandedRows : collapsedRows);
        });
    }
}
