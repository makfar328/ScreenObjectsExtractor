package org.makar.ocrapp.screenobjectsextractor.view.main.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.makar.ocrapp.screenobjectsextractor.model.common.SelectedObjectClass;

import java.util.function.Consumer;

public class ObjectClassTagFactory {

    /**
     * Создает и добавляет новый "тег" выбранного класса объекта в FlowPane.
     * @param model
     * @param onRemove действие, выполнить (удалить) для передаваемого элемента тип HBox
     * @return
     */
    public static HBox createTag(SelectedObjectClass model, Consumer<HBox> onRemove) {
        HBox tagBox = new HBox(5);
        tagBox.getStyleClass().add("object-class-tag");
        tagBox.setUserData(model.getClassName());

        Label nameLabel = new Label(model.getClassName());
        nameLabel.getStyleClass().add("object-class-tag-name");

        Label countLabel = new Label();
        countLabel.getStyleClass().add("object-class-tag-count");
        countLabel.textProperty().bind(model.countProperty().asString());

        Button plusButton = createButton(" +", () -> model.incrementCount());
        Button minusButton = createButton("-", () -> model.decrementCount());
        Button removeButton = createButton("X", () -> onRemove.accept(tagBox));

        HBox plusMinusHBox = new HBox(2, plusButton, new Label("/"), minusButton);
        plusMinusHBox.setAlignment(Pos.CENTER);

        tagBox.getChildren().addAll(nameLabel, countLabel, plusMinusHBox, removeButton);
        return tagBox;
    }

    private static Button createButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("tag-button");
        button.setOnAction(event -> action.run());
        return button;
    }
}
