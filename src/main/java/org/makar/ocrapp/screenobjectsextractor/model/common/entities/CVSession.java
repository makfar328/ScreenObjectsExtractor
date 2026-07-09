package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import java.awt.image.BufferedImage;

public class CVSession {
    private long sessionId;
    private BufferedImage capturedImage; // Поле для хранения изображения

    public CVSession(long sessionId, BufferedImage capturedImage) {
        this.sessionId = sessionId;
        this.capturedImage = capturedImage;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public BufferedImage getCapturedImage() {
        return capturedImage;
    }

    public void setCapturedImage(BufferedImage capturedImage) {
        this.capturedImage = capturedImage;
    }
}