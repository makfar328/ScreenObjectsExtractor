package org.makar.ocrapp.screenobjectsextractor.view.texteditor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.view.Common;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
public class TextEditorUITest {

    private TextEditorController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws IOException {
        URL fxmlUrl = MainApplication.class.getResource("fxml/text-editor-view.fxml");
        assertNotNull(fxmlUrl, "text-editor-view.fxml not found on classpath");

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        controller = loader.getController();
        this.stage = stage;
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
        stage.toFront();
    }

    @Test
    @DisplayName("АЭ-12 : Нажатие closeButton закрывает text-editor-view, " +
            "screen-analysis-results-view становится снова активным")
    void oneClickCloseButton_removesTexEditor_showsScreenAnalysisResultsWindow(FxRobot robot) {

        Stage[] resultsStageHolder = new Stage[1];
        Platform.runLater(() -> {
            Stage resultsStage = new Stage();
            resultsStage.setTitle("screen-analysis-results (test stand-in)");
            resultsStage.setScene(new Scene(new Pane(), 100, 100));
            resultsStage.show();
            stage.toFront();
            resultsStageHolder[0] = resultsStage;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Предусловие: оба окна изначально видны
        assertTrue(stage.isShowing(), "text-editor-view должен быть показан до клика на closeButton");
        assertTrue(resultsStageHolder[0].isShowing(), "screen-analysis-results-view должен быть показан до клика на closeButton");

        robot.clickOn("#closeButton");
        WaitForAsyncUtils.waitForFxEvents();

        // 1. text-editor-view закрыт
        assertFalse(stage.isShowing(),
                "text-editor stage должен быть закрыт после нажатия closeButton");

        // 2. screen-analysis-results-view по-прежнему показан —
        assertTrue(resultsStageHolder[0].isShowing(),
                "screen-analysis-results-view должен оставаться видимым " +
                        "после закрытия text-editor-view");
    }

    // ── АЭ-13 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("АЭ-13: Нажатие saveButton открывает FileChooser и сохраняет содержимое textArea в выбранный файл")
    void saveButton_opensFileChooserAndPersistsTextAreaContent(FxRobot robot) throws IOException {

        // Arrange 1: текст в редакторе
        String expectedText = "OCR-текст для сохранения в файл";
        Platform.runLater(() -> controller.setCurrentText(expectedText));
        WaitForAsyncUtils.waitForFxEvents();

        // Arrange 2: временный файл-«цель»
        Path tempFile = Files.createTempFile("ae13_test", ".txt");

        // Arrange 3: флаг «диалог был вызван» + подмена провайдера
        boolean[] dialogCalled = {false};
        Platform.runLater(() ->
                controller.setFileDialogProvider(owner -> {
                    dialogCalled[0] = true;
                    return tempFile.toFile();      // имитируем выбор файла пользователем
                })
        );
        WaitForAsyncUtils.waitForFxEvents();

        try {
            robot.clickOn("#saveButton");
            WaitForAsyncUtils.waitForFxEvents();

            // Assert 1: диалог выбора файла был вызван
            assertTrue(dialogCalled[0],
                    "saveButton должен вызывать диалог выбора файла (FileDialogProvider.showSaveDialog)");

            // Assert 2: файл существует и содержит правильный текст
            assertTrue(Files.exists(tempFile),
                    "Файл должен существовать после вызова saveButton");
            String savedContent = Files.readString(tempFile, StandardCharsets.UTF_8).trim();
            assertEquals(expectedText, savedContent,
                    "Содержимое файла должно совпадать с текстом из textArea");

            // Assert 3: окно остаётся открытым
            assertTrue(stage.isShowing(),
                    "TextEditor-окно должно оставаться открытым после сохранения файла");

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

}