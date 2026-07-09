package org.makar.ocrapp.screenobjectsextractor.view;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D; // Используем JavaFX Rectangle2D для координат
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle; // javafx.scene.shape.Rectangle для UI-элементов
import javafx.scene.shape.Shape;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/* Класс наследует Stage, то есть является окном. Это нужно, чтобы создать обработчики событий.
 * Модуль предоставляет механизм выделения прямоугольной области (зажать ЛКМ, выделить, отжать ЛКМ)
 * и передает координаты выделенной области. */

public class ScreenOverlay extends Stage {
    private double startX, startY;
    private Rectangle selectionRectangle = new Rectangle();
    private Rectangle fullScreenRect;
    private Shape shadeShape;

    // Теперь принимает уже готовый полноэкранный снимок и Consumer для Rectangle2D (координат выделения)
    public ScreenOverlay(BufferedImage fullScreenImage, Consumer<Rectangle2D> onSelectionComplete) {

        WritableImage fxImage = SwingFXUtils.toFXImage(fullScreenImage, null);
        ImageView backgroundImage = new ImageView(fxImage);

        double screenW = Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = Screen.getPrimary().getVisualBounds().getHeight();

        Pane root = new Pane();
        root.getChildren().add(backgroundImage);

        fullScreenRect = new Rectangle(0, 0, screenW, screenH);
        selectionRectangle = new Rectangle(0, 0, 0, 0);

        shadeShape = fullScreenRect;
        shadeShape.setFill(Color.rgb(0, 0, 0, 0.2));
        root.getChildren().add(shadeShape);

        selectionRectangle.setFill(Color.TRANSPARENT);
        selectionRectangle.setStroke(Color.BLUE);
        selectionRectangle.setStrokeWidth(2);
        root.getChildren().add(selectionRectangle);

        root.setOnMousePressed(event -> {
            startX = event.getX();
            startY = event.getY();
            selectionRectangle.setX(startX);
            selectionRectangle.setY(startY);
            selectionRectangle.setWidth(0);
            selectionRectangle.setHeight(0);

            root.getChildren().remove(shadeShape);
            shadeShape = fullScreenRect;
            shadeShape.setFill(Color.rgb(0, 0, 0, 0.2));
            root.getChildren().add(1, shadeShape);
        });

        root.setOnMouseDragged(event -> {
            double endX = event.getX();
            double endY = event.getY();
            double x = Math.min(startX, endX);
            double y = Math.min(startY, endY);
            double w = Math.abs(endX - startX);
            double h = Math.abs(endY - startY);

            selectionRectangle.setX(x);
            selectionRectangle.setY(y);
            selectionRectangle.setWidth(w);
            selectionRectangle.setHeight(h);

            root.getChildren().remove(shadeShape);
            Rectangle hole = new Rectangle(x, y, w, h);
            shadeShape = Shape.subtract(fullScreenRect, hole);
            shadeShape.setFill(Color.rgb(0, 0, 0, 0.2));
            root.getChildren().add(1, shadeShape);
        });

        root.setOnMouseReleased(event -> {
            this.close();
            double x = selectionRectangle.getX();
            double y = selectionRectangle.getY();
            double w = selectionRectangle.getWidth();
            double h = selectionRectangle.getHeight();

            if (w > 0 && h > 0) { // Передаем координаты только если выделение было корректным
                onSelectionComplete.accept(new Rectangle2D(x, y, w, h));
            } else {
                onSelectionComplete.accept(null); // Если выделения не было или оно некорректно
            }
        });

        Scene scene = new Scene(root, screenW, screenH);
        scene.setFill(Color.TRANSPARENT);
        this.initStyle(StageStyle.TRANSPARENT);
        this.setScene(scene);
        this.setAlwaysOnTop(true);
    }
}