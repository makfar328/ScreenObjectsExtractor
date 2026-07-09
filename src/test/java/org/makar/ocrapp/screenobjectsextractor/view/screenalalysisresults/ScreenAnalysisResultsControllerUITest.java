package org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.model.common.OCRAppDetectedObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults.ScreenAnalysisResultsController;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScreenAnalysisResultsControllerUITest {

    private ScreenAnalysisResultsController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        URL fxmlUrl = MainApplication.class
                .getResource("fxml/screen-analysis-results-view.fxml");
        assertNotNull(fxmlUrl, "screen-analysis-results-view.fxml not found on classpath");

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        controller = loader.getController();
        this.stage = stage;
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
        stage.toFront();
    }

    @Order(1)
    @Test
    @DisplayName("АЭ-03: setCapturedImage с непустым изображением → capturedImageView отображает изображение")
    void setCapturedImage_withValidImage_capturedImageViewDisplaysIt(FxRobot robot)
            throws Exception {

        //  Arrange: минимальный тестовый BufferedImage 
        int width  = 200;
        int height = 100;
        BufferedImage fakeCapture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = fakeCapture.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        //  Act: имитируем вызов из ControllerFactory 
        // setCapturedImage() должен выполняться в JAT — SwingFXUtils.toFXImage()
        // и ImageView.setImage() являются операциями JavaFX
        Platform.runLater(() -> controller.setCapturedImage(fakeCapture));
        WaitForAsyncUtils.waitForFxEvents();

        //  Assert 
        ImageView capturedImageView = controller.capturedImageView;

        // 1. Изображение установлено
        Image fxImage = capturedImageView.getImage();
        assertNotNull(fxImage,
                "capturedImageView.getImage() не должен быть null после setCapturedImage");

        // 2. Размеры соответствуют исходному BufferedImage
        assertEquals(width,  (int) fxImage.getWidth(),
                "Ширина fxImage должна совпадать с шириной BufferedImage");
        assertEquals(height, (int) fxImage.getHeight(),
                "Высота fxImage должна совпадать с высотой BufferedImage");

        // 3. ImageView видим (не hidden, не managed=false)
        assertTrue(capturedImageView.isVisible(),
                "capturedImageView должен быть видимым");
    }

    //  АЭ-04 
    @Order(2)
    @Test
    @DisplayName("АЭ-04: Если OCR нашёл текст — objectListVBox содержит хотя бы один элемент")
    void recognizedText_withResults_objectListVBoxContainsItems(FxRobot robot) {

        BufferedImage fakeCapture = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

        TextObject fakeText = new TextObject("Hello OCR", 10, 10, 80, 20);

        Platform.runLater(() -> {
            controller.setCapturedImage(fakeCapture);
            controller.setRecognizedText(List.of(fakeText));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(
                controller.objectListVBox.getChildren().isEmpty(),
                "objectListVBox должен содержать хотя бы один объект");
    }

    //  АЭ-05 
    @Order(3)
    @Test
    @DisplayName("АЭ-05: Если OCR не нашёл текст — objectListVBox содержит Label «Текст не найден или не распознан.»")
    void recognizedText_withEmptyList_displaysNoTextLabel(FxRobot robot) {
        
        Platform.runLater(() -> controller.setRecognizedText(Collections.emptyList()));
        WaitForAsyncUtils.waitForFxEvents();

        //  Assert 1: ровно один дочерний узел (clear() уже был вызван) 
        ObservableList<Node> children = controller.objectListVBox.getChildren();
        assertEquals(1, children.size(),
                "objectListVBox должен содержать ровно один элемент при пустом результате OCR");

        //  Assert 2: тип узла — Label 
        assertInstanceOf(Label.class, children.get(0),
                "Единственный дочерний узел должен быть javafx.scene.control.Label");

        //  Assert 3: текст сообщения точно совпадает 
        Label noTextLabel = (Label) children.get(0);
        assertEquals(
                "Текст не найден или не распознан.",
                noTextLabel.getText(),
                "Текст Label должен точно соответствовать строке из displayTextObjects()"
        );
    }

    // ─── АЭ-06 ──────────────────────────────────────────────────────────────────
    @Order(4)
    @Test
    @DisplayName("АЭ-06: Если CV обнаружил объекты — область результатов CV содержит хотя бы один элемент")
    void detectedObjects_withResults_objectListVBoxContainsItems(FxRobot robot) throws Exception {

        // Размер 200×100 px; bounding box [0.1, 0.1, 0.3, 0.3] даёт crop 20,10,60,30
        BufferedImage fakeCapture = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

        OCRAppDetectedObject fakeObject =
                new OCRAppDetectedObject("cat", 0.95, 0.1, 0.1, 0.3, 0.3);

        // ocrAppDetectedObjects — private; публичного сеттера нет.
        // Инжектируем список через reflection до того, как нажмём кнопку.
        Field ocrAppDetectedObjectsField = ScreenAnalysisResultsController.class
                .getDeclaredField("ocrAppDetectedObjects");
        ocrAppDetectedObjectsField.setAccessible(true);

        Platform.runLater(() -> {
            try {
                controller.setCapturedImage(fakeCapture);
                ocrAppDetectedObjectsField.set(
                        controller,
                        new java.util.ArrayList<>(List.of(fakeObject))
                );
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#detectedObjectsButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(
                controller.objectListVBox.getChildren().isEmpty(),
                "objectListVBox должен содержать хотя бы один элемент при наличии результатов CV"
        );
    }

    //  АЭ-07
    @Order(5)
    @Test
    @DisplayName("АЭ-07: Если CV не обнаружил объектов — objectListVBox содержит Label «Ничего не найдено.»")
    void detectedObjects_withNoData_displaysNothingFoundLabel(FxRobot robot) {
        
        robot.clickOn("#detectedObjectsButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Assert 1: ровно один дочерний узел
        ObservableList<Node> children = controller.objectListVBox.getChildren();
        assertEquals(1, children.size(),
                "objectListVBox должен содержать ровно один элемент при отсутствии данных CV");

        //  Assert 2: тип узла — Label 
        assertInstanceOf(javafx.scene.control.Label.class, children.get(0),
                "Единственный дочерний узел должен быть javafx.scene.control.Label");

        //  Assert 3: текст сообщения точно совпадает 
        Label noObjectsLabel = (Label) children.get(0);
        assertEquals(
                "Ничего не найдено.",
                noObjectsLabel.getText(),
                "Текст Label должен точно соответствовать строке из displayDetectedObjects()"
        );
    }

    //  АЭ-08
    @Order(6)
    @Test
    @DisplayName("АЭ-08: Клик по элементу результата OCR обводит соответствующую область на capturedImageView")
    void oneClickOnRecognizedTextElement_highLightRectangle(FxRobot robot) {

        BufferedImage fakeCapture = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

        TextObject fakeText = new TextObject("Hello OCR", 10, 10, 80, 20);

        Platform.runLater(() -> {
            controller.setCapturedImage(fakeCapture);
            controller.setRecognizedText(List.of(fakeText));
        });
        WaitForAsyncUtils.waitForFxEvents();

        Node textCardItem = controller.objectListVBox.getChildren().get(0);
        robot.clickOn(textCardItem);
        WaitForAsyncUtils.waitForFxEvents();

        //Rectangle rectangle = (Rectangle) stage.getScene().lookup("#highlightRectangle");
        //Rectangle rectangle = robot.lookup("#highlightRectangle").queryAs(Rectangle.class);
        Rectangle rectangle = controller.highlightRectangle;

        assertTrue(
                controller.imageDisplayAnchorPane.getChildren().contains(rectangle),
                "highlightRectangle должен быть в imageDisplayAnchorPane после клика на текстовый элемент"
        );

        assertTrue(rectangle.xProperty().isBound(),
                "xProperty должна быть привязана");
        assertTrue(rectangle.yProperty().isBound(),
                "yProperty должна быть привязана");
        assertTrue(rectangle.widthProperty().isBound(),
                "widthProperty должна быть привязана");
        assertTrue(rectangle.heightProperty().isBound(),
                "heightProperty должна быть привязана");
    }

    // АЭ-09
    @Order(7)
    @Test
    @DisplayName("Клик по элементу результата CV обводит соответствующую область на capturedImageView")
    void oneClickOnDetectedObjectElement_highLightRectangle(FxRobot robot) {

        BufferedImage fakeCapture = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

        OCRAppDetectedObject object = new OCRAppDetectedObject("cat",  0.95, 0.1, 0.1, 0.3, 0.3);

        Platform.runLater(() -> {
            controller.setCapturedImage(fakeCapture);
            controller.setDetectedObjects(List.of(object));
        });
        robot.clickOn("#detectedObjectsButton");
        WaitForAsyncUtils.waitForFxEvents();

        Node cvCardItem = controller.objectListVBox.getChildren().get(0);
        robot.clickOn(cvCardItem);
        WaitForAsyncUtils.waitForFxEvents();

        Rectangle rectangle = controller.highlightRectangle;

        assertTrue(
                controller.imageDisplayAnchorPane.getChildren().contains(rectangle),
                "highlightRectangle должен быть в imageDisplayAnchorPane после клика на текстовый элемент"
        );

        assertTrue(rectangle.xProperty().isBound(),
                "xProperty должна быть привязана");
        assertTrue(rectangle.yProperty().isBound(),
                "yProperty должна быть привязана");
        assertTrue(rectangle.widthProperty().isBound(),
                "widthProperty должна быть привязана");
        assertTrue(rectangle.heightProperty().isBound(),
                "heightProperty должна быть привязана");
    }

    // АЭ-10
    @Order(8)
    @Test
    @DisplayName("АЭ-10: Закрытие screen-analysis-results-view возвращает пользователя на main-view")
    void closeResultsView_mainViewBecomesVisible(FxRobot robot) {

        // Создаём «главное» окно, имитирующее primaryStage.
        Stage[] fakeMainStageHolder = new Stage[1];

        Platform.runLater(() -> {
            Stage fakeMainStage = new Stage();
            fakeMainStageHolder[0] = fakeMainStage;
            fakeMainStage.hide();

            stage.setOnHidden(event -> fakeMainStage.show());

            controller.setStage(stage);
        });
        WaitForAsyncUtils.waitForFxEvents();


        // stage.close() → stage переходит в состояние hidden → срабатывает onHidden → fakeMainStage.show()
        Platform.runLater(() -> stage.close());
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(
                fakeMainStageHolder[0].isShowing(),
                "После закрытия screen-analysis-results-view main-view (primaryStage) " +
                        "должен стать видимым — setOnHidden должен вызвать primaryStage.show()"
        );
    }

    //  АЭ-11
    @Order(9)
    @Test
    @DisplayName("АЭ-11: Двойной клик по элементу результата OCR открывает text-editor-view, " +
            "при этом screen-analysis-results-view остаётся открытым, но становится неактивным")
    void doubleClickOnRecognizedTextElement_opensTextEditor(FxRobot robot) {

        BufferedImage fakeCapture = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        String ocrText = "Hello OCR";
        TextObject fakeText = new TextObject(ocrText, 10, 10, 80, 20);
        Stage[] fakeEditorStageHolder = new Stage[1];

        Platform.runLater(() -> {
            controller.setCapturedImage(fakeCapture);
            controller.setRecognizedText(List.of(fakeText));
        });
        WaitForAsyncUtils.waitForFxEvents();

        List<Window> windowsBefore = new ArrayList<>(Window.getWindows());

        Node textCardItem = controller.objectListVBox.getChildren().get(0);
        robot.doubleClickOn(textCardItem);
        WaitForAsyncUtils.waitForFxEvents();

        List<Window> newWindows = Window.getWindows().stream()
                .filter(w -> !windowsBefore.contains(w))
                .collect(Collectors.toList());

        // 1. Открылось хотя бы одно новое окно
        assertFalse(
                newWindows.isEmpty(),
                "После двойного клика должно открыться новое окно (text-editor-view)"
        );

        // 2. Новое окно — именно Stage, а не системный Popup/tooltip
        assertInstanceOf(
                Stage.class,
                newWindows.get(0),
                "text-editor-view должен быть экземпляром Stage"
        );

        Stage textEditorStage = (Stage) newWindows.get(0);

        // 3. Редактор отображается
        assertTrue(
                textEditorStage.isShowing(),
                "text-editor-view должен отображаться после двойного клика"
        );

        // 4. screen-analysis-results-view остаётся ОТКРЫТЫМ (не скрыт и не закрыт).
        assertTrue(
                stage.isShowing(),
                "screen-analysis-results-view должен оставаться открытым после открытия text-editor-view"
        );

        TextArea textArea = (TextArea) textEditorStage.getScene().lookup("#textArea");

        // 5. В textArea окна text-editor-view содержится текст, соответствующий элементу, по которому был совершён двойной клик
        assertTrue(
                textArea.getText().equals(ocrText),
                "textArea из text-editor-view должен содержать текст, эквивалентный тексту нажатого элемента из screen-analysis-results-view"
        );

    }

}