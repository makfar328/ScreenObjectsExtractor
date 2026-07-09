package org.makar.ocrapp.screenobjectsextractor.view.main.components;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ObjectClassSelector {

    private final Popup classSelectionPopup = new Popup();
    private final ListView<String> classListView;
    private final TextField triggerField;
    private final Consumer<String> onItemSelected;

    /** Неизменяемая копия полного списка — источник правды для фильтрации */
    private final ObservableList<String> allItems;

    public ObjectClassSelector(TextField triggerField,
                               ObservableList<String> items,
                               Consumer<String> onItemSelected) {
        this.triggerField = triggerField;
        this.onItemSelected = onItemSelected;
        this.allItems = FXCollections.observableArrayList(items); // копия, не ссылка

        this.classListView = new ListView<>(FXCollections.observableArrayList(items));
        classListView.getStyleClass().add("list-view");
        classListView.setPrefSize(200, 100);

        classSelectionPopup.getContent().add(classListView);
        classSelectionPopup.setAutoHide(true);

        setupListeners();
    }

    private void setupListeners() {

        // ── 1. Фокус — показать/скрыть Popup ─────────────────────────────
        triggerField.focusedProperty().addListener((obs, old, isNowFocused) -> {
            if (isNowFocused) {
                show();
            } else {
                classSelectionPopup.hide();
            }
        });

        // ── 2. Ввод текста — фильтровать список ───────────────────────────
        triggerField.textProperty().addListener((obs, oldText, newText) -> {
            applyFilter(newText);
            // Если Popup вдруг скрылся, снова показываем
            if (!classSelectionPopup.isShowing() && triggerField.isFocused()) {
                show();
            }
        });

        // ── 3. Выбор элемента в ListView ──────────────────────────────────
        classListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> {
                    if (selected != null) {
                        if (!selected.equalsIgnoreCase("any object class")) {
                            onItemSelected.accept(selected);
                        }
                        triggerField.clear();          // сброс текста → applyFilter("") → полный список
                        classListView.getSelectionModel().clearSelection(); // чтобы можно было выбрать повторно
                        classSelectionPopup.hide();
                    }
                });

        // ── 4. Скрытие Popup — сброс фокуса ──────────────────────────────
        classSelectionPopup.setOnHidden(event -> {
            Window window = triggerField.getScene().getWindow();
            if (window != null && window.getScene() != null) {
                window.getScene().getRoot().requestFocus();
            }
        });
    }

    /**
     * Фильтрует classListView по подстроке (регистр не учитывается).
     * Пустая строка → полный список.
     */
    private void applyFilter(String text) {
        if (text == null || text.isBlank()) {
            classListView.setItems(FXCollections.observableArrayList(allItems));
        } else {
            String lower = text.toLowerCase();
            ObservableList<String> filtered = allItems.stream()
                    .filter(item -> item.toLowerCase().contains(lower))
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));
            classListView.setItems(filtered);
        }
    }

    private void show() {
        if (!classSelectionPopup.isShowing()) {
            double x = triggerField.localToScreen(0, 0).getX();
            double y = triggerField.localToScreen(0, 0).getY()
                    + triggerField.getLayoutBounds().getHeight();
            classSelectionPopup.show(triggerField, x, y);
            classListView.setPrefWidth(triggerField.getWidth());
        }
    }

    public Popup getClassSelectionPopup() {
        return classSelectionPopup;
    }
}