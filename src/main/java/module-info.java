module org.makar.ocrapp.screenobjectsextractor {
    //  Зависимости UI
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires eu.hansolo.tilesfx;

    //  Зависимости AI / ML
    requires tess4j;
    requires com.microsoft.onnxruntime;
    requires ai.djl.api;

    requires org.slf4j;

    //  Зависимости инфраструктуры
    requires com.google.gson;
    requires java.sql;
    requires jdk.compiler;

    //  JDK-модули, используемые явно
    // java.desktop: BufferedImage, Rectangle (java.awt) используются в OcrService
    // и ObjectDetectionService. Без этого requires компилятор не видит пакет java.awt.
    requires java.desktop;
    requires org.apache.commons.logging;

    //  Открытие пакетов для javafx.fxml (рефлексивный доступ FXML-загрузчика)
    opens org.makar.ocrapp.screenobjectsextractor to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view.main to javafx.fxml, org.testfx;
    opens org.makar.ocrapp.screenobjectsextractor.view.screencapture to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view.texteditor to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view.screenalalysisresults to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view.selecteddirectories to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.view.journal to javafx.fxml;

    opens org.makar.ocrapp.screenobjectsextractor.model.common.entities to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.common to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.core to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.core.indexer to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.core.search to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase to javafx.fxml;
    opens org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase to javafx.fxml;

    // открытие пакетов для org.testfx
    //opens org.makar.ocrapp.screenobjectsextractor.view.main to org.testfx;
    //opens org.makar.ocrapp.screenobjectsextractor.view.main to org.testfx;

    //  Экспорт публичного API
    exports org.makar.ocrapp.screenobjectsextractor;
    exports org.makar.ocrapp.screenobjectsextractor.view;
    exports org.makar.ocrapp.screenobjectsextractor.model.common;
    exports org.makar.ocrapp.screenobjectsextractor.model.common.entities;
    exports org.makar.ocrapp.screenobjectsextractor.model.core;
    exports org.makar.ocrapp.screenobjectsextractor.model.core.indexer;
    exports org.makar.ocrapp.screenobjectsextractor.model.core.search;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.searchSessionDatabase;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.persistence.fileIndexDatabase;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2;
    exports org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v1;
}