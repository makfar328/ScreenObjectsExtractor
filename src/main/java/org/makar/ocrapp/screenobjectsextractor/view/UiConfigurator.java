package org.makar.ocrapp.screenobjectsextractor.view;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.makar.ocrapp.screenobjectsextractor.view.main.MainController;
import org.makar.ocrapp.screenobjectsextractor.view.main.components.MainPaneComponents;

import java.io.InputStream;

public class UiConfigurator {
    /*public static void configureMainPane( Stage stage,
                                          AnchorPane anchorPane_layer1,
                                          SplitPane splitPane_layer2_prevAnchorPane,
                                          AnchorPane anchorPane1_layer3_prevSplitPane,
                                          AnchorPane anchorPane2_layer3_prevSplitPane,
                                          SplitPane splitPane_layer4_prevAnchorPane,
                                          VBox vBox_layer4_prevAnchorPane,
                                          AnchorPane AnchorPane1_layer5_prevSplitPane,
                                          AnchorPane AnchorPane2_layer5_prevSplitPane,
                                          Button startDecomposeButton,
                                          Button searchSettingsButton,
                                          Button openJournalButton,
                                          Region topButton1Spacer,
                                          Region button1Button2Spacer,
                                          Region button2Button3Spacer,
                                          Region button3BottomSpacer) {*/

    public static void configureMainPane(MainPaneComponents components) {

        /* Заметки:
        * зацикливание биндингов размеров приводит к StackOverflow (Runtime + спам в консоли) */
        // Контейнеры
        components.anchorPane_layer1.setPrefWidth(100);
        components.anchorPane_layer1.setPrefHeight(100);

        /*splitPane_layer2_prevAnchorPane.getDividers().get(0).positionProperty().addListener((o, newV, oldV) -> {
            splitPane_layer2_prevAnchorPane.setDividerPosition(0, 0.05);
        });*/
        //splitPane_layer2_prevAnchorPane.setDividerPositions(0.25);
        AnchorPane.setTopAnchor(components.splitPane_layer2_prevAnchorPane, 0.0);
        AnchorPane.setLeftAnchor(components.splitPane_layer2_prevAnchorPane, 0.0);
        AnchorPane.setRightAnchor(components.splitPane_layer2_prevAnchorPane, 0.0);
        AnchorPane.setBottomAnchor(components.splitPane_layer2_prevAnchorPane, 0.0);


        components.splitPane_layer4_prevAnchorPane.setDividerPositions(0.1);
        components.splitPane_layer4_prevAnchorPane.setOrientation(Orientation.VERTICAL);
        AnchorPane.setTopAnchor(components.splitPane_layer4_prevAnchorPane, 0.0);
        AnchorPane.setLeftAnchor(components.splitPane_layer4_prevAnchorPane, 0.0);
        AnchorPane.setRightAnchor(components.splitPane_layer4_prevAnchorPane, 0.0);
        AnchorPane.setBottomAnchor(components.splitPane_layer4_prevAnchorPane, 0.0);

/*        components.anchorPane1_layer3_prevSplitPane.setMinWidth(0);
        components.anchorPane1_layer3_prevSplitPane.setMinHeight(0);
        AnchorPane.setTopAnchor(components.anchorPane1_layer3_prevSplitPane, 0.0);
        AnchorPane.setLeftAnchor(components.anchorPane1_layer3_prevSplitPane, 0.0);
        AnchorPane.setRightAnchor(components.anchorPane1_layer3_prevSplitPane, 0.0);
        AnchorPane.setBottomAnchor(components.anchorPane1_layer3_prevSplitPane, 0.0);*/

        components.anchorPane2_layer3_prevSplitPane.setMinWidth(0);
        components.anchorPane2_layer3_prevSplitPane.setMinHeight(0);
        AnchorPane.setTopAnchor(components.anchorPane2_layer3_prevSplitPane, 0.0);
        AnchorPane.setLeftAnchor(components.anchorPane2_layer3_prevSplitPane, 0.0);
        AnchorPane.setRightAnchor(components.anchorPane2_layer3_prevSplitPane, 0.0);
        AnchorPane.setBottomAnchor(components.anchorPane2_layer3_prevSplitPane, 0.0);


        components.vBox_layer4_prevAnchorPane.setAlignment(javafx.geometry.Pos.CENTER);
        AnchorPane.setTopAnchor(components.vBox_layer4_prevAnchorPane, 0.0);
        AnchorPane.setLeftAnchor(components.vBox_layer4_prevAnchorPane, 0.0);
        AnchorPane.setRightAnchor(components.vBox_layer4_prevAnchorPane, 0.0);
        AnchorPane.setBottomAnchor(components.vBox_layer4_prevAnchorPane, 0.0);

        double buttonScale = 0.8; // 80% ширины панели

/*
        components.startDecomposeButton.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(buttonScale));
        components.startDecomposeButton.prefHeightProperty().bind(components.startDecomposeButton.prefWidthProperty());

        components.openSettingsButton.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(buttonScale));
        components.openSettingsButton.prefHeightProperty().bind(components.openSettingsButton.prefWidthProperty());

        components.openJournalButton.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(buttonScale));
        components.openJournalButton.prefHeightProperty().bind(components.openJournalButton.prefWidthProperty());
*/

/*

        */
/* регионы между кнопками, математика heightXwidth = (1)X(0.8*3 + 0.15*4) = 1X3 *//*

        components.topButton1Spacer.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(0.13));
        components.topButton1Spacer.prefHeightProperty().bind(components.topButton1Spacer.prefWidthProperty());

        components.button1Button2Spacer.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(0.13));
        components.button1Button2Spacer.prefHeightProperty().bind(components.button1Button2Spacer.prefWidthProperty());

        components.button2Button3Spacer.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(0.13));
        components.button2Button3Spacer.prefHeightProperty().bind(components.button2Button3Spacer.prefWidthProperty());

        components.button3BottomSpacer.prefWidthProperty().bind(components.anchorPane1_layer3_prevSplitPane.widthProperty().multiply(0.1));
        components.button3BottomSpacer.prefHeightProperty().bind(components.button3BottomSpacer.prefWidthProperty());
*/

    }

    static public void setSideMenuState(AnchorPane sideMenu, boolean expanded) {
        // Для всех кнопок side-menu
        applySideMenuStateRecursive(sideMenu, expanded);
    }

    private static void applySideMenuStateRecursive(Node node, boolean expanded) {
        if (node instanceof Button) {
            setLabelsOpacity((Button) node, expanded ? 1.0 : 0.0);
        }

        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applySideMenuStateRecursive(child, expanded);
            }
        }
    }

    // рекурсивно ищет все Label и устанавливает в них передаваемое свойство opacity
    private static void setLabelsOpacity(Button button, double opacity) {
        setLabelsOpacityRecursive(button, opacity);
    }

    private static void setLabelsOpacityRecursive(Node node, double opacity) {
        if (node instanceof Label) {
            node.setOpacity(opacity);
        } else if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                setLabelsOpacityRecursive(child, opacity);
            }
        }
    }


    /**
     * Инициализирует ImageView изображением из ресурса или внешнего URL.
     * @param imageView
     * @param resourcePathOrUrl например, "/org/makar/ocrapp/screenobjectsextractor/icons/photo-error-pomp.png"
     */
    public static void initializeImageView(ImageView imageView, String resourcePathOrUrl) {
        try {
            if (resourcePathOrUrl == null || resourcePathOrUrl.isEmpty()) {
                throw new IllegalArgumentException("Путь к изображению не задан");
            }

            InputStream is = UiConfigurator.class.getResourceAsStream(resourcePathOrUrl);
            if (is == null) {
                throw new IllegalArgumentException("Ресурс не найден: " + resourcePathOrUrl);
            }

            imageView.setImage(new Image(is));
            System.err.println(imageView + " проинициализирован с изображением из " + resourcePathOrUrl);

        } catch (Exception e) {
            System.err.println("Ошибка при загрузке изображения: " + e.getMessage());

            InputStream fallback = UiConfigurator.class.getResourceAsStream(
                    "/org/makar/ocrapp/screenobjectsextractor/icons/photo-error-pomp.png"
            );
            if (fallback != null) {
                imageView.setImage(new Image(fallback));
            }
        }
    }

    public static void configureValidationPane(Stage stage,
                                               AnchorPane anchorPane_layer1,
                                               VBox vBox_layer2_prevAnchorPane,
                                               HBox hBox1_layer3_prevVBox,
                                               SplitPane splitPane_layer3_prevVBox,
                                               HBox hBox2_layer3_prevVBox) {
        anchorPane_layer1.setMinWidth(900);
        anchorPane_layer1.setMinHeight(600);

        // prefHeight="400.0" prefWidth="600.0"
        anchorPane_layer1.setPrefWidth(400);
        anchorPane_layer1.setPrefHeight(600);

        AnchorPane.setTopAnchor(vBox_layer2_prevAnchorPane, 0.0);
        AnchorPane.setLeftAnchor(vBox_layer2_prevAnchorPane, 0.0);
        AnchorPane.setRightAnchor(vBox_layer2_prevAnchorPane, 0.0);
        AnchorPane.setBottomAnchor(vBox_layer2_prevAnchorPane, 0.0);


        AnchorPane.setLeftAnchor(hBox1_layer3_prevVBox, 0.0);
        AnchorPane.setRightAnchor(hBox1_layer3_prevVBox, 0.0);


        AnchorPane.setLeftAnchor(splitPane_layer3_prevVBox, 0.0);
        AnchorPane.setRightAnchor(splitPane_layer3_prevVBox, 0.0);


        AnchorPane.setLeftAnchor(hBox2_layer3_prevVBox, 0.0);
        AnchorPane.setRightAnchor(hBox2_layer3_prevVBox, 0.0);


    }

}
