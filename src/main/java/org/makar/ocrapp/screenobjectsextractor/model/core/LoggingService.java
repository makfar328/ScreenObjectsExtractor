package org.makar.ocrapp.screenobjectsextractor.model.core;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.LogEntry;
import org.makar.ocrapp.screenobjectsextractor.model.core.log.JournalLogHandler;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingService {

    private final JournalLogHandler journalLogHandler;

    public LoggingService() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.ALL);

        boolean alreadyAttached = Arrays.stream(root.getHandlers())
                .anyMatch(h -> h instanceof JournalLogHandler);

        if (!alreadyAttached) {
            this.journalLogHandler = new JournalLogHandler();
            root.addHandler(journalLogHandler);
        } else {
            this.journalLogHandler = (JournalLogHandler) Arrays.stream(root.getHandlers())
            .filter(h -> h instanceof JournalLogHandler)
                    .findFirst().orElseThrow();
        }
    }

    /** JournalController вызывает этот метод — знает только об этом контракте */
    public ObservableList<LogEntry> getLogEntries() {
        return journalLogHandler.getLogEntries();
    }

}
