package org.makar.ocrapp.screenobjectsextractor.view.main.components;

import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainPaneComponents {
    public final Stage stage;
    public final AnchorPane anchorPane_layer1;
    public final SplitPane splitPane_layer2_prevAnchorPane;
    public final AnchorPane anchorPane1_layer3_prevSplitPane;
    public final AnchorPane anchorPane2_layer3_prevSplitPane;
    public final SplitPane splitPane_layer4_prevAnchorPane;
    public final VBox vBox_layer4_prevAnchorPane;
    public final AnchorPane AnchorPane1_layer5_prevSplitPane;
    public final AnchorPane AnchorPane2_layer5_prevSplitPane;
    public final Button startDecomposeButton;
    public final Button openSettingsButton;
    public final Button openJournalButton;
    public final Region topButton1Spacer;
    public final Region button1Button2Spacer;
    public final Region button2Button3Spacer;
    public final Region button3BottomSpacer;

    public MainPaneComponents(Stage stage, AnchorPane ap1, SplitPane sp2, AnchorPane ap1_3, AnchorPane ap2_3,
                              SplitPane sp4, VBox vb4, AnchorPane ap1_5, AnchorPane ap2_5,
                              Button startBtn, Button settingsBtn, Button journalBtn,
                              Region s1, Region s2, Region s3, Region s4) {
        this.stage = stage;
        this.anchorPane_layer1 = ap1;
        this.splitPane_layer2_prevAnchorPane = sp2;
        this.anchorPane1_layer3_prevSplitPane = ap1_3;
        this.anchorPane2_layer3_prevSplitPane = ap2_3;
        this.splitPane_layer4_prevAnchorPane = sp4;
        this.vBox_layer4_prevAnchorPane = vb4;
        this.AnchorPane1_layer5_prevSplitPane = ap1_5;
        this.AnchorPane2_layer5_prevSplitPane = ap2_5;
        this.startDecomposeButton = startBtn;
        this.openSettingsButton = settingsBtn;
        this.openJournalButton = journalBtn;
        this.topButton1Spacer = s1;
        this.button1Button2Spacer = s2;
        this.button2Button3Spacer = s3;
        this.button3BottomSpacer = s4;
    }
}
