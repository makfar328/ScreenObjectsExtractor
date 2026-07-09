package org.makar.ocrapp.screenobjectsextractor.view.screencapture;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.Consumer;

public class CaptureButtonController {

    @FXML public Circle buttonShape;
    @FXML private StackPane rootPane; // Корневая панель из FXML
    @FXML private ImageView captureImageView; // ImageView для иконки

    private double xOffset = 0;
    private double yOffset = 0;
    private double initialMouseX;
    private double initialMouseY;
    private Consumer<Void> onCaptureInitiated;
    private Stage stage; // Ссылка на текущий Stage этой кнопки

    @FXML
    public void initialize() {

        captureImageView = new ImageView();

        try {
            Image image = new Image(getClass().getResourceAsStream("/org/makar/ocrapp/screenobjectsextractor/icons/scissors.png"));
            if (image.isError()) {
                System.err.println("(CaptureButtonController) Warning: Failed to load image. Error: " + image.exceptionProperty().get().getMessage());
            } else {
                captureImageView = new ImageView(image);
                System.out.println("Image loaded successfully. Width: " + image.getWidth() + ", Height: " + image.getHeight());


                double circlePadding = 10.0;
                buttonShape.radiusProperty().bind(
                        Bindings.min(rootPane.widthProperty().divide(2).subtract(circlePadding / 2),
                                rootPane.heightProperty().divide(2).subtract(circlePadding / 2))
                );

                double imageScale = 0.85;
                captureImageView.fitWidthProperty().bind(buttonShape.radiusProperty().multiply(2).multiply(imageScale));
                captureImageView.fitHeightProperty().bind(buttonShape.radiusProperty().multiply(2).multiply(imageScale));
                captureImageView.setPreserveRatio(true);

                rootPane.getChildren().add(captureImageView);
            }
        } catch (NullPointerException e) {
            System.err.println("(CaptureButtonController) Warning: Failed to load image from resources" + e.getMessage());
        }

        // Логика для перетаскивания кнопки
        rootPane.setOnMousePressed(event -> {
            initialMouseX = event.getScreenX();
            initialMouseY = event.getScreenY();
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        rootPane.setOnMouseDragged(event -> {
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        rootPane.setOnMouseReleased(event -> {
            double deltaX = Math.abs(event.getScreenX() - initialMouseX);
            double deltaY = Math.abs(event.getScreenY() - initialMouseY);
            // Если перемещение было менее 5 пикселей, считаем это кликом
            if (deltaX < 5 && deltaY < 5) {
                if (onCaptureInitiated != null) {
                    onCaptureInitiated.accept(null); /* Consumer - Запускаем процесс захвата */
                }
                if (stage != null) {
                    stage.close(); // Закрываем кнопку после клика
                }
            }
        });

    }

    /**
     * Метод для установки колбэка, который будет вызван при клике по кнопке.
     * @param onCaptureInitiated Колбэк для запуска процесса захвата экрана.
     */
    public void setOnCaptureInitiated(Consumer<Void> onCaptureInitiated) {
        this.onCaptureInitiated = onCaptureInitiated;
    }

    /**
     * Устанавливает Stage, связанный с этим контроллером, и настраивает его начальное положение.
     * @param stage Stage, в котором отображается эта кнопка.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        // Настраиваем стиль окна: прозрачное, всегда поверх других
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        // Устанавливаем начальное положение (в правом верхнем углу основного экрана)
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        // Используем фактические размеры rootPane, заданные в FXML
        stage.setX(screenBounds.getMaxX() - rootPane.getPrefWidth() - 50); // 50px от правого края
        stage.setY(screenBounds.getMinY() + 50); // 50px от верхнего края
    }
}