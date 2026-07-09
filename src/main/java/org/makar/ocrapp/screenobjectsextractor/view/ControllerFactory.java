package org.makar.ocrapp.screenobjectsextractor.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.makar.ocrapp.screenobjectsextractor.view.journal.JournalController;
import org.makar.ocrapp.screenobjectsextractor.view.main.ServiceBundle;
import org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults.ScreenAnalysisResultsController;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

// пакет: org.makar.ocrapp.screenobjectsextractor.view
public class ControllerFactory implements IControllerFactory {

    private static final String FXML_BASE =
            "/org/makar/ocrapp/screenobjectsextractor/fxml/";

    private final ServiceBundle bundle;

    public ControllerFactory(ServiceBundle bundle) {
        this.bundle = bundle;
    }

    // ── Публичный API ─────────────────────────────────────────────────────

    @Override
    public LoadedView<JournalController> loadJournalView() {
        LoadedView<JournalController> view = loadFxml("journal-view.fxml");

        JournalController c = view.controller();
        c.setSearchSessionService(bundle.searchSessionService());
        c.setDecomposeSessionService(bundle.decomposeSessionService());
        c.setLoggingService(bundle.loggingService());
        c.onReady();

        return view;
    }

    @Override
    public LoadedView<ScreenAnalysisResultsController> loadScreenAnalysisView(BufferedImage capturedImage) {
        LoadedView<ScreenAnalysisResultsController> view = loadFxml("screen-analysis-results-view.fxml");

        ScreenAnalysisResultsController c = view.controller();
        c.setCapturedImage(capturedImage); // Передать захваченное изображение
        c.setImageContentAnalyzer(bundle.imageContentAnalyzer());
        c.setDecomposeSessionService(bundle.decomposeSessionService());
        c.setFileIndexService(bundle.fileIndexService());
        c.setAiExecutorService(bundle.aiExecutorService());

        return view;
    }

    // ── Приватный помощник ────────────────────────────────────────────────

    /**
     * Загружает FXML по имени файла. Тип контроллера определяется
     * вызывающим методом — тот знает, какой контроллер у каждого FXML.
     *
     * @throws ControllerLoadException если файл не найден или содержит ошибки
     */
    private <C> LoadedView<C> loadFxml(String filename) {
        URL location = getClass().getResource(FXML_BASE + filename);
        if (location == null) {
            throw new ControllerLoadException(filename,
                    new FileNotFoundException("Ресурс не найден: " + FXML_BASE + filename));
        }
        try {
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();
            C controller = loader.getController();
            return new LoadedView<>(controller, root);
        } catch (IOException e) {
            throw new ControllerLoadException(filename, e);
        }
    }
}
