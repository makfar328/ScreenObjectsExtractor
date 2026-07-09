package org.makar.ocrapp.screenobjectsextractor.view;

import javafx.scene.Parent;

public record LoadedView<C>(C controller, Parent root) {}
