package org.makar.ocrapp.screenobjectsextractor.view.selecteddirectories;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.makar.ocrapp.screenobjectsextractor.model.common.SearchDirectoryConfig;
import org.makar.ocrapp.screenobjectsextractor.model.core.search.SearchDirectoryService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SelectedDirectoriesController {

    private final static Logger logger = Logger.getLogger(SelectedDirectoriesController.class.getName());

    @FXML private VBox directoriesListVBox;

    private ObservableList<SearchDirectoryConfig> directories;
    private Consumer<List<SearchDirectoryConfig>> onDirectoryChanged;
    private SearchDirectoryService searchDirectoryService;

    @FXML
    public void initialize() {

    }

    public void setSearchDirectoryService(SearchDirectoryService searchDirectoryService) {
        this.searchDirectoryService = searchDirectoryService;
    }

    public void setSelectedSearchDirectories(ObservableList<SearchDirectoryConfig> directories) {
        this.directories = directories;
        refreshDirectoriesList();
        
        this.directories.addListener((javafx.collections.ListChangeListener<SearchDirectoryConfig>) change -> {
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved()) {
                    refreshDirectoriesList();
                }
            }
        });
    }

    public void setOnDirectoryChanged(Consumer<List<SearchDirectoryConfig>> callback) {
        this.onDirectoryChanged = callback;
    }

    private void notifyDirectoriesChanged() {
        if (onDirectoryChanged != null) {
            onDirectoryChanged.accept(new ArrayList<>(this.directories));
        }
    }

    @FXML
    private void handleOkButton() {
        notifyDirectoriesChanged();
        ((Stage) directoriesListVBox.getScene().getWindow()).close();
    }


    private void refreshDirectoriesList() {
        directoriesListVBox.getChildren().clear();
        if (directories == null || directories.isEmpty()) {
            System.out.println("No directories found");
            Label noDirectoriesLabel = new Label("Каталоги для поиска не выбраны");
            directoriesListVBox.getChildren().add(noDirectoriesLabel);
        } else {
            System.out.println("Found " + directories.size() + " directories");
            for (int i = 0; i < directories.size(); i++) {
                SearchDirectoryConfig config = directories.get(i);
                directoriesListVBox.getChildren().add(creatDirectoryItem(i, config));
            }
        }
    }

    private HBox creatDirectoryItem(int index, SearchDirectoryConfig config) {
        HBox itemBox = new HBox();
        itemBox.getStyleClass().add("item-box");
        itemBox.setSpacing(10);
        itemBox.setPrefHeight(40);
        itemBox.setMinHeight(40);
        itemBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(itemBox, Priority.ALWAYS);
        itemBox.setPadding(new Insets(10, 10, 10, 10));

        AnchorPane.setLeftAnchor(itemBox, 4.0);
        AnchorPane.setRightAnchor(itemBox, 4.0);

        // номер директории
        Label indexLabel = new Label(String.valueOf(index + 1));
        itemBox.getChildren().add(indexLabel);

        // путь
        Label pathLabel = new Label(truncatePath(config.getDirectory(), 40));
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        pathLabel.setEllipsisString("...");
        pathLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        pathLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        itemBox.getChildren().add(pathLabel);

        // глубина поиска, int, изменяемая
        Spinner<Integer> deapthSpinner = new Spinner<>();
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, config.getSearchDepth());
        deapthSpinner.setValueFactory(valueFactory);
        deapthSpinner.setEditable(true);
        deapthSpinner.setPrefWidth(70);
        itemBox.getChildren().add(deapthSpinner);

        deapthSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                config.setSearchDepth(newValue);

                if (directories != null && searchDirectoryService != null) {
                    List<SearchDirectoryConfig> absorbedConfigs = searchDirectoryService.applyAbsorptionLogic(new ArrayList<>(directories));
                    Platform.runLater(() -> directories.setAll(absorbedConfigs));
                }
            }
        });

        itemBox.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {

            Node targetNode = (Node) event.getTarget();
            boolean isSpinnerClick = false;

            while (targetNode != itemBox) {
                if (targetNode == deapthSpinner) {
                    isSpinnerClick = true;
                }
                if (targetNode != null) {
                    targetNode = targetNode.getParent();
                }
            }

            if (isSpinnerClick) {
                event.consume();
                return;
            }

            //Button deleteButton = (Button) event.getSource();
            Button deleteButton = new Button("Убрать из рассмотрения");

            Popup popup = new Popup();
            popup.getContent().add(deleteButton);
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);
            deleteButton.setOnAction(deleteEvent -> {
                directories.remove(config);
                popup.hide();
                notifyDirectoriesChanged();
            });
            popup.show(itemBox.getScene().getWindow(), event.getScreenX(), event.getScreenY());

        });

        return itemBox;
    }

    private String truncatePath(Path directoryPath, int maxLength) {
        String directoryPathAsString = directoryPath.toString();
        if (directoryPathAsString.length() <= maxLength) {
            return directoryPathAsString;
        }

        int halfLength = (maxLength - 3) / 2;
        String start = directoryPathAsString.substring(0, halfLength);
        String end = directoryPathAsString.substring(directoryPathAsString.length() - (maxLength - halfLength - 3));
        directoryPathAsString = start + "..." + end;

        return directoryPathAsString;
    }
}
