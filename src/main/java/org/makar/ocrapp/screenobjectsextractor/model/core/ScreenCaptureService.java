package org.makar.ocrapp.screenobjectsextractor.model.core;


import java.awt.Rectangle;
import javafx.stage.Screen;

import java.awt.*;
import java.awt.image.BufferedImage;

/* Захват экрана.
* Сервис для создания фотографии по набору свойств, достаточному для соответствующих инструментов.
* Используется в MainController.
* */
public class ScreenCaptureService {

    public static BufferedImage captureFullScreen() throws AWTException {
        Rectangle fullScreenRectangle = new Rectangle(
                (int) Screen.getPrimary().getVisualBounds().getMinX(),
                (int) Screen.getPrimary().getVisualBounds().getMinY(),
                (int) Screen.getPrimary().getVisualBounds().getMaxX(),
                (int) Screen.getPrimary().getVisualBounds().getMaxY()
        );
        BufferedImage fullScreenCapture = new Robot().createScreenCapture(fullScreenRectangle);
        return fullScreenCapture;
    }

    public static BufferedImage captureScreen(int x, int y, int width, int height) throws AWTException {
        if (x < 0 || y < 0) {
            return null;
        }
        Rectangle screenRectangle = new Rectangle(x, y, width, height);
        BufferedImage screenCapture = new Robot().createScreenCapture(screenRectangle);
        return screenCapture;
    }

}
