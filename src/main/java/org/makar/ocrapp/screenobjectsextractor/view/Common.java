package org.makar.ocrapp.screenobjectsextractor.view;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Common {

    // метод для установления факта связи узлов сцены (наследник или один и тот же)
    private boolean isAncestorOrSelf(Node ancestor, Node node) {
        if (ancestor.equals(node)) {
            System.out.println(">>> DEBUG: Node " + node + " IS " + ancestor + " (self).");
            return true; /* self */
        }
        Parent parent = node.getParent();
        while (parent != null) {
            if (ancestor.equals(parent)) {
                System.out.println(">>> DEBUG: Node " + node + " is a descendant of " + ancestor + " (parent was " + parent + ").");
                return true; /* ancestor */
            } else {
                System.out.println(">>> DEBUG: Parent " + parent + " is not " + ancestor + " for node " + node + ".");
                parent = parent.getParent();
            }
        }
        System.out.println(">>> DEBUG: Node " + node + " is NOT " + ancestor + " or its descendant.");
        return false;
    }

    // Метод для настройки размеров Stage с пропорцией сторон aspectRatio
    public static void adjustStageDimension(Stage stage, double newSize, double aspectRatio, boolean isWidth) {
        if (isWidth) {
            stage.setHeight(newSize / aspectRatio);
        } else {
            stage.setWidth(newSize * aspectRatio);
        }
    }

    // Методы для показа уведомлений (инфо, предупреждение, ошибка)
    public static void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}