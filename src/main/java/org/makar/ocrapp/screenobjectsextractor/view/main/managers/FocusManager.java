package org.makar.ocrapp.screenobjectsextractor.view.main.managers;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;

public class FocusManager {
    public static void setupGlobalFocusHandling(Parent root, TextArea area, TextField field, Popup popup) {
        // Снятие фокуса при клике мимо
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Node target = (Node) event.getTarget();
            if (area != null && area.isFocused() && !isAncestorOrSelf(area, target)) {
                root.requestFocus();
            }
        });

        // ESC для закрытия попапов и сброса фокуса
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                if (popup != null && popup.isShowing()) {
                    popup.hide();
                    event.consume();
                } else if (field != null && field.isFocused()) {
                    root.requestFocus();
                    event.consume();
                }
            }
        });
    }

    private static boolean isAncestorOrSelf(Node target, Node node) {
        return target == node || (node != null && isAncestorOrSelf(target, node.getParent()));
    }

    /*
    primaryStage.getScene().getRoot().addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> { // <-- Использование addEventFilter
            System.out.println(">>> DEBUG: Mouse pressed event DETECTED on Scene's Root (via filter)! Target: " + event.getTarget());
            Node target = (Node) event.getTarget();

            // Проверяем, если searchQueryTextArea существует, находится в фокусе и клик был НЕ на нём или его дочернем элементе
            if (searchQueryTextArea != null && searchQueryTextArea.isFocused() && !isAncestorOrSelf(searchQueryTextArea, target)) {
                primaryStage.getScene().getRoot().requestFocus(); // Запрашиваем фокус на корневом элементе
                System.out.println("Focus requested on root to unfocus TextArea.");
            }
        });
     */

    /*primaryStage.getScene().getRoot().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
        Node target = (Node) event.getTarget();
        boolean insideSideMenu = OCRAppUtils.isDescendantOf(target, anchorPane1_layer3_prevSplitPane);
        if (insideSideMenu) {
                splitPane_layer2_prevAnchorPane.setDividerPosition(0, 0.25);
                UiConfigurator.setSideMenuState(anchorPane1_layer3_prevSplitPane,true);
        } else {
                splitPane_layer2_prevAnchorPane.setDividerPosition(0, 0.05);
                UiConfigurator.setSideMenuState(anchorPane1_layer3_prevSplitPane,false);
        }
    });*/

    /* Снятие фокуса при нажатии Esc; метод setOnKeyPressed регистрирует обработчик событий
        EventHandler в фазе всплытия. getScene().setOnKeyPressed() оказался не надежным, возникли проблемы
        Поэтому заменён на более надежный регистратор обработчика событий */
        /*primaryStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                primaryStage.getScene().getRoot().requestFocus();
            }
        });*/
}