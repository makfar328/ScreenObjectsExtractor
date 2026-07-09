package org.makar.ocrapp.screenobjectsextractor.view;

import org.makar.ocrapp.screenobjectsextractor.view.journal.JournalController;
import org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults.ScreenAnalysisResultsController;

import java.awt.image.BufferedImage;

public interface IControllerFactory {

    /**
     * Загружает journal-view.fxml, инжектирует SearchSessionService
     * и DecomposeSessionService в JournalController.
     */
    LoadedView<JournalController> loadJournalView();

    /**
     * Загружает screen-analysis.fxml, инжектирует DecomposeSessionService
     * в ScreenAnalysisResultsController.
     */
    LoadedView<ScreenAnalysisResultsController> loadScreenAnalysisView(BufferedImage image);
}
