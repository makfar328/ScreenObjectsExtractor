package org.makar.ocrapp.screenobjectsextractor.view.journal;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.makar.ocrapp.screenobjectsextractor.MainApplication;
import org.makar.ocrapp.screenobjectsextractor.model.common.FileMetadata;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.DecomposeSessionEntry;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.LogEntry;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.SearchSession;
import org.makar.ocrapp.screenobjectsextractor.model.core.DecomposeSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.core.LoggingService;
import org.makar.ocrapp.screenobjectsextractor.model.core.SearchSessionService;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.decomposeSessionDatabase.IDecomposeSessionRepository;
import org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase.ISearchSessionRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class JournalController {

    @FXML private Button searchSessionsTabButton;
    @FXML private Button decomposeSessionsTabButton;
    @FXML private Button systemLogsTabButton;
    @FXML public StackPane journalContentPane;
    @FXML private Stage stage;

    private SearchSessionService searchSessionService;
    private DecomposeSessionService decomposeSessionService;
    private LoggingService loggingService;
    private ListView<LogEntry> systemLogsListView;
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        // TODO: Реализовать переключение разделов и загрузку соответствующего содержимого
        System.out.println("JournalController initialized");
        searchSessionsTabButton.setOnAction(e -> showSearchSessions());
        decomposeSessionsTabButton.setOnAction(e -> showDecomposeSessions());
        systemLogsTabButton.setOnAction(e -> showSystemLogs());
    }

    /** Вызывается из ControllerFactory после всех setXxx() */
    public void onReady() {
        showSystemLogs();  // начальная вкладка
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setSearchSessionService(SearchSessionService s) {
        this.searchSessionService = s;
    }

    public void setDecomposeSessionService(DecomposeSessionService s) {
        this.decomposeSessionService = s;
    }

    public void setLoggingService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    /**
     * Стратегия показа диалога подтверждения.
     * В production: показывает реальный javafx.scene.control.Alert.
     * В тестах: инжектируется мок через setConfirmationDialog().
     */
    @FunctionalInterface
    public interface ConfirmationDialog {
        Optional<ButtonType> show(String title, String header, String content);
    }

    // Default-реализация — использует реальный Alert
    private ConfirmationDialog confirmationDialog = (title, header, content) -> {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert.showAndWait();
    };

    /** Setter для подмены в тестах */
    public void setConfirmationDialog(ConfirmationDialog dialog) {
        this.confirmationDialog = dialog;
    }

    private void showSearchSessions() {
        if (stage != null) stage.setTitle("Сессии поиска");
        journalContentPane.getChildren().clear();

        // ── Observable-список — передаётся в лямбду кнопки очистки ──────────
        ObservableList<SearchSession> items =
                FXCollections.observableArrayList(searchSessionService.findAll());

        // ── TableView ────────────────────────────────────────────────────────
        TableView<SearchSession> table = new TableView<>();
        table.setId("searchSessionsTable");                       // для lookup в тестах
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Placeholder (виден автоматически, когда items пуст)
        Label emptyPlaceholder = new Label("Список сессий поиска пуст");
        emptyPlaceholder.setId("searchSessionsPlaceholder");
        table.setPlaceholder(emptyPlaceholder);

        // ── Колонки ──────────────────────────────────────────────────────────
        TableColumn<SearchSession, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        cell.getValue().getSessionId()));
        idCol.setMaxWidth(60);

        TableColumn<SearchSession, String> startCol = new TableColumn<>("Начало");
        startCol.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        LOCAL_DATE_TIME_FORMATTER.format(cell.getValue().getStartedAt())));

        TableColumn<SearchSession, String> endCol = new TableColumn<>("Конец");
        endCol.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        LOCAL_DATE_TIME_FORMATTER.format(cell.getValue().getFinishedAt())));

        TableColumn<SearchSession, Integer> countCol = new TableColumn<>("Найдено");
        countCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        cell.getValue().getFilesCount()));
        countCol.setMaxWidth(80);

        TableColumn<SearchSession, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus()));

        table.getColumns().addAll(idCol, startCol, endCol, countCol, statusCol);
        table.setItems(items);

        // ── Кнопка «Очистить журнал» ─────────────────────────────────────────
        Button clearButton = new Button("Очистить журнал");
        clearButton.setId("clearJournalButton");                  // для clickOn в тестах
        clearButton.setOnAction(e -> handleClearSearchSessions(items));

        HBox toolbar = new HBox(10, clearButton);

        // ── Layout ───────────────────────────────────────────────────────────
        VBox layout = new VBox(10, toolbar, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        journalContentPane.getChildren().add(layout);
    }

// ── Изменить handleClearSearchSessions() ────────────────────────────────

    private void handleClearSearchSessions(ObservableList<SearchSession> items) {
        confirmationDialog.show(
                "Очистка журнала",
                "Удалить все сессии поиска?",
                "Это действие необратимо."
        ).ifPresent(result -> {
            if (result == ButtonType.OK) {
                searchSessionService.deleteAll();
                items.clear();
            }
        });
    }

    private void showDecomposeSessions() {
        if (stage != null) stage.setTitle("Сессии декомпозиции");
        journalContentPane.getChildren().clear();

        TableView<DecomposeSessionEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DecomposeSessionEntry, String> timeCol = new TableColumn<>("Время");
        timeCol.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().getProcessedAt() != null ?
                                cell.getValue().getProcessedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                                : "—"));

        TableColumn<DecomposeSessionEntry, String> sourceCol = new TableColumn<>("Источник");
        sourceCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCaptureSource() != null ?
                        cell.getValue().getCaptureSource() : "—"));
        sourceCol.setMaxWidth(130);

        TableColumn<DecomposeSessionEntry, String> fileCol = new TableColumn<>("Файл");
        fileCol.setCellValueFactory(cell -> {
            FileMetadata fm = cell.getValue().getFileMetadata();
            return new SimpleStringProperty(
                    (fm != null && fm.getFileName() != null) ? fm.getFileName() : "—");
        });

        TableColumn<DecomposeSessionEntry, String> sizeCol = new TableColumn<>("Размер");
        sizeCol.setCellValueFactory(cell -> {
            FileMetadata fm = cell.getValue().getFileMetadata();
            Long size = (fm != null) ? fm.getFileSize() : null;
            if (size == null || size <= 0) return new SimpleStringProperty("—");
            if (size >= 1024 * 1024)
                return new SimpleStringProperty(String.format("%.1f MB", size / (1024.0 * 1024)));
            if (size >= 1024)
                return new SimpleStringProperty(size / 1024 + " KB");
            return new SimpleStringProperty(size + " B");
        });
        sizeCol.setMaxWidth(90);

        TableColumn<DecomposeSessionEntry, String> dimensionsCol = new TableColumn<>("Разрешение");
        dimensionsCol.setCellValueFactory(cell -> {
            FileMetadata fm = cell.getValue().getFileMetadata();
            if (fm == null || fm.getImageWidth() <= 0) return new SimpleStringProperty("—");
            return new SimpleStringProperty(fm.getImageWidth() + "×" + fm.getImageHeight());
        });
        dimensionsCol.setMaxWidth(100);

        TableColumn<DecomposeSessionEntry, String> textsCol = new TableColumn<>("Текстов");
        textsCol.setCellValueFactory(cell -> {
            FileMetadata fm = cell.getValue().getFileMetadata();
            if (fm == null || fm.getRecognizedTextContent() == null)
                return new SimpleStringProperty("—");
            return new SimpleStringProperty(String.valueOf(fm.getRecognizedTextContent().size()));
        });
        textsCol.setMaxWidth(70);

        TableColumn<DecomposeSessionEntry, String> objectsCol = new TableColumn<>("Объектов");
        objectsCol.setCellValueFactory(cell -> {
            FileMetadata fm = cell.getValue().getFileMetadata();
            if (fm == null || fm.getDetectedObjects() == null)
                return new SimpleStringProperty("—");
            return new SimpleStringProperty(String.valueOf(fm.getDetectedObjects().size()));
        });
        objectsCol.setMaxWidth(80);

        table.getColumns().addAll(
                timeCol, sourceCol, fileCol, sizeCol, dimensionsCol, textsCol, objectsCol);
        table.setItems(
                FXCollections.observableArrayList(decomposeSessionService.findAll()));

        journalContentPane.getChildren().add(table);
    }


    private void showSystemLogs() {
        if (stage != null) stage.setTitle("Системные логи");
        journalContentPane.getChildren().clear();
        System.out.println("Showing System Logs tab...");

        systemLogsListView = new ListView<>();
        ObservableList<LogEntry> allLogs = loggingService.getLogEntries();
        ObservableList<LogEntry> filteredLogs = allLogs.filtered(
                log -> log.getLoggerName() != null && log.getLoggerName().startsWith("org.makar")
        );
        System.out.println("JournalController: Number of filtered log entries: " + filteredLogs.size());

        systemLogsListView.setItems(filteredLogs);

        if (!filteredLogs.isEmpty()) {
            systemLogsListView.getSelectionModel().select(0);
        }

        systemLogsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(LogEntry log, boolean empty) {
                super.updateItem(log, empty);

                if (empty || log == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: #313233;");
                } else {

                    String loggerName = log.getLoggerName();
                    if (loggerName.startsWith("org.makar.ocrapp.screenobjectsextractor")) {
                        loggerName = "~/" + loggerName.substring("org.makar.ocrapp.screenobjectsextractor".length());
                    }

                    long millis = log.getTimestamp();
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
                    String formatedTime = LOG_TIME_FORMATTER.format(dateTime);

                    String logText = String.format("%s [%s] %s: %s", formatedTime, log.getLevel().getName(), loggerName, log.getMessage());

                    setText(logText);
                    String textColor;
                    switch (log.getLevel().getName()) {
                        case "SEVERE":
                        case "ERROR":
                            textColor = "red";
                            break;
                        case "WARNING":
                            textColor = "#c07c00";
                            break;
                        case "INFO":
                            textColor = "#2cc22c";
                            break;
                        case "DEBUG":
                            textColor = "blue";
                            break;
                        default:
                            textColor = "gray";
                            break;
                    }
                    setStyle("-fx-background-color: #313233; -fx-text-fill: " + textColor + ";");
                }
            }
        });

        journalContentPane.getChildren().add(systemLogsListView);
    }


    private Label createHeaderLabel(String text, int width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #e0e0e0;");
        return label;
    }

    private Label createValueLabel(String text, int width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setStyle("-fx-font-weight: normal; -fx-text-fill: #e0e0e0;");
        return label;
    }
}