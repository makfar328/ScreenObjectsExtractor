package org.makar.ocrapp.screenobjectsextractor.model.common.entities;

public class TextObject {
    private String text;
    private int x;
    private int y;
    private int width;
    private int height;
    private final OcrLevel level;

    public TextObject() {
        this.level = OcrLevel.LINE;
    }

    public TextObject(String text, int x, int y, int width, int height, OcrLevel level) {

        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.level = level;

    }

    public TextObject(String text, int x, int y, int width, int height) {
        this(text, x, y, width, height, OcrLevel.LINE);
    }

    public String getText() {return text;}
    public void setText(String text) {this.text = text;}
    public int getX() {return x;}
    public void setX(int x) {this.x = x;}
    public int getY() {return y;}
    public void setY(int y) {this.y = y;    }
    public int getWidth() {return width;}
    public void setWidth(int width) {this.width = width;}
    public int getHeight() {return height;}
    public void setHeight(int height) {this.height = height;}
    public OcrLevel getLevel() { return level; }

    /**
     * Уровень иерархии OCR-результата.
     * Значения совпадают с константами Tesseract PageIteratorLevel.
     */
    public enum OcrLevel {
        BLOCK(0),       // весь текстовый блок
        PARAGRAPH(1),   // абзац
        LINE(2),        // строка
        WORD(3),        // слово
        SYMBOL(4);      // символ

        public final int tesseractLevel;

        OcrLevel(int tesseractLevel) {
            this.tesseractLevel = tesseractLevel;
        }
    }

}
