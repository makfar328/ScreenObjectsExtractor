package org.makar.ocrapp.screenobjectsextractor.view.texteditor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.makar.ocrapp.screenobjectsextractor.view.Common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TextEditorController {

    @FXML private TextArea textArea; // Поле для текстовой области, инжектируется из FXML
    @FXML private Button saveButton; // Поле для кнопки "Сохранить", инжектируется из FXML
    @FXML private Button closeButton; // Поле для кнопки "Закрыть", инжектируется из FXML

    private Stage stage;
    private String currentText; // Текст, который будет установлен извне
    private FileDialogProvider fileDialogProvider;

    @FXML
    public void initialize() {

        fileDialogProvider = owner -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            return fc.showSaveDialog(owner);
        };

        textArea.setWrapText(true);

        if (currentText != null) {
            textArea.setText(currentText);
        }

    }

    /**
     * Провайдер диалога сохранения.
     * По умолчанию — настоящий FileChooser.
     * В тестах заменяется через setFileDialogProvider().
     */
    @FunctionalInterface
    public interface FileDialogProvider {
        /** Показывает диалог выбора файла и возвращает File или null (отмена). */
        java.io.File showSaveDialog(Stage owner);
    }

    /** Setter для тестов — позволяет подменить диалог без bytecode-инструментации. */
    public void setFileDialogProvider(FileDialogProvider provider) {
        this.fileDialogProvider = provider;
    }

    // Этот метод вызывается извне (например, из ValidationController) для установки текста
    public void setCurrentText(String text) {
        this.currentText = text;
        if (textArea != null) {
            textArea.setText(currentText);
        }
    }

    // Обработчик для кнопки "Сохранить"
    @FXML
    private void handleSaveButtonAction() {
        String editedText = textArea.getText();
        // Получаем Stage (окно), которому принадлежит текущая сцена, используя любой элемент UI
        Stage ownerStage = (Stage) saveButton.getScene().getWindow();
        saveTextToFile(editedText, ownerStage);
    }

    /**
     * Сохраняет переданный текст в файл, выбранный пользователем.
     */
    private void saveTextToFile(String text, Stage ownerStage) {
        /*FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить текст как...");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fileChooser.showSaveDialog(ownerStage);*/

        File file = fileDialogProvider.showSaveDialog(stage); // java.io
        if (file == null) return;

        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            Common.showInfoAlert("Успех", "Текст успешно сохранен", "Текст был сохранен в файл: " + file.getAbsolutePath());
        } catch (IOException e) {
            Common.showErrorAlert("Ошибка", "Ошибка сохранения файла", "Не удалось сохранить текст в файл: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Обработчик для кнопки "Закрыть"
    @FXML
    private void handleCloseButtonAction() {
        // Получаем Stage (окно), которому принадлежит текущая сцена, используя любой элемент UI
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close(); // Закрываем окно
        System.out.println("Окно редактирования текстового элемента закрыто.");
    }
}