package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextObjects {
    private List<TextObject> textObjectList;
    private long sessionId;

    public TextObjects() {
        sessionId = -1;
        textObjectList = new ArrayList<>();
    }

    public TextObjects(Long sessionId) {
        this.sessionId = sessionId;
        textObjectList = new ArrayList<TextObject>();
    }

    public TextObjects(List<TextObject> textObjectList, Long sessionId) {
        this.textObjectList = textObjectList;
        this.sessionId = sessionId;
    }

    public List<TextObject> getTextObjectList() {
        return Collections.unmodifiableList(textObjectList);
    }

    public void setTextObjectList(List<TextObject> textObjectList) {
        this.textObjectList = textObjectList;
    }

    public void addTextObject(TextObject textObject) {
        textObjectList.add(textObject);
    }

    public void removeTextObject(int i) {
        textObjectList.remove(i);
    }

    public TextObject index(int i) {
        return textObjectList.get(i);
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }
}
