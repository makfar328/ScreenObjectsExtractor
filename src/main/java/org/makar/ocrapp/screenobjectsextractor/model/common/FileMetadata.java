package org.makar.ocrapp.screenobjectsextractor.model.common;


import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/* Модель данных для хранения извлеченных метаданных о файлах (путь, имя, дата, возможно, хеш). */
public class FileMetadata {
    // id filepath filename fileExtension fileSize creationDate modificationDate recognizedText detectedObjectClasses
    private long id;
    private Path filePath;
    private String fileName;
    private String fileExtension;
    private Long fileSize;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
    private List<TextObject> recognizedTextContent;
    private List<OCRAppDetectedObject> detectedObjects;
    private int imageWidth;   // 0 если не определён (не изображение / не проанализирован)
    private int imageHeight;

    public FileMetadata(long id, Path filePath, String fileName, String fileExtension, Long fileSize,
                        LocalDateTime creationDate, LocalDateTime modificationDate,
                        List<TextObject> recognizedTextContent, List<OCRAppDetectedObject> detectedObjects,
                        int imageWidth, int imageHeight) {
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.fileSize = fileSize;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.recognizedTextContent = (recognizedTextContent != null) ? List.copyOf(recognizedTextContent) : new ArrayList<>();
        this.detectedObjects = (detectedObjects != null) ? List.copyOf(detectedObjects) : new ArrayList<>();
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public FileMetadata(Path filePath, String fileName, String fileExtension, long fileSize,
                        LocalDateTime creationDate, LocalDateTime modificationDate,
                        List<TextObject> recognizedTextContent, List<OCRAppDetectedObject> detectedObjects,
                        int imageWidth, int imageHeight) {
        this(-1, filePath, fileName, fileExtension, fileSize, creationDate,
                modificationDate, recognizedTextContent, detectedObjects, imageWidth, imageHeight);
    }

    public FileMetadata(Path filePath, String fileName, String fileExtension, long fileSize,
                        LocalDateTime creationDate, LocalDateTime modificationDate,
                        List<TextObject> recognizedTextContent, List<OCRAppDetectedObject> detectedObjects) {
        this(-1, filePath, fileName, fileExtension, fileSize, creationDate,
                modificationDate, recognizedTextContent, detectedObjects, 0, 0);
    }

    public FileMetadata(long id, Path filePath, String fileName, String fileExtension, Long fileSize, LocalDateTime creationDate, LocalDateTime modificationDate, List<TextObject> recognizedTextContent, List<OCRAppDetectedObject> detectedObjects) {
        this(id, filePath, fileName, fileExtension, fileSize, creationDate,
                modificationDate, recognizedTextContent, detectedObjects, 0, 0);
    }

    public long getId() {
        return id;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getModificationDate() {
        return modificationDate;
    }

    public List<TextObject> getRecognizedTextContent() {
        return recognizedTextContent;
    }

    public List<OCRAppDetectedObject> getDetectedObjects() {
        return detectedObjects;
    }

    public int getImageWidth()  { return imageWidth; }

    public int getImageHeight() { return imageHeight; }

    public void setId(long id) {
        this.id = id;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public void setModificationDate(LocalDateTime modificationDate) {
        this.modificationDate = modificationDate;
    }

    public void setRecognizedTextContent(List<TextObject> recognizedTextContent) {
        this.recognizedTextContent = recognizedTextContent;
    }

    public void setDetectedObjects(List<OCRAppDetectedObject> detectedObjectClasses) {
        this.detectedObjects = detectedObjectClasses;
    }

    public void setImageWidth(int imageWidth)   { this.imageWidth = imageWidth; }

    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }


    /* сравнение даты модификации */
    public boolean isUpToDate(FileMetadata currentFsMetadata) {
        if (currentFsMetadata == null) { return false; }
        if(this.getModificationDate() == null || currentFsMetadata.getModificationDate() == null) {
            return !Objects.equals(this.getModificationDate(), currentFsMetadata.getModificationDate());
        }
        return this.getModificationDate().isBefore(currentFsMetadata.getModificationDate());
    }


    public void showObjects() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // stackTrace[0] — это getStackTrace, [1] — showObjects, [2] — вызывающий метод
        String callerClassName = "Unknown";
        if (stackTrace.length > 2) {
            callerClassName = stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName();
        }

        if (detectedObjects == null) {
            System.out.println("Detected objects is null (called from " + callerClassName + ")");
        }if (detectedObjects.isEmpty()) {
            System.out.println("Detected objects is createEmpty (called from " + callerClassName + ")");
        } else {
            System.out.println("This image contains the following objects (called from " + callerClassName + "):");
            for (OCRAppDetectedObject detectedObject : detectedObjects) {
                System.out.println(detectedObject.toString());
            }
        }
    }

    public boolean isEmpty() {
        /*return (this.createEmpty().equals(this));*/
        /*return filePath == null;*/
        return filePath == null && (fileName == null || fileName.isEmpty());
    }

    public static FileMetadata createEmpty() {
        return new FileMetadata(
                -1L,          // id — sentinel "нет ID"
                null,         // filePath — обязательное поле: null = нет реального файла
                null,         // fileName
                null,         // fileExtension
                0L,           // fileSize
                null,         // creationDate
                null,         // modificationDate
                List.of(),    // recognizedTextContent
                List.of(),    // detectedObjects
                0,            // imageWidth
                0             // imageHeight
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return Objects.equals(filePath, that.filePath) &&
                Objects.equals(fileName, that.fileName); // Или сравнивайте по filePath, если id еще не уникален
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath); // Или Objects.hash(filePath)
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "filePath=" + filePath +
                ", fileName='" + fileName + '\'' +
                ", fileExtension='" + fileExtension + '\'' +
                ", fileSize=" + fileSize +
                ", imageWidth=" + imageWidth +
                ", imageHeight=" + imageHeight +
                ", creationDate=" + creationDate +
                ", modificationDate=" + modificationDate +
                ", recognizedTextContent=" + recognizedTextContent +
                ", detectedObjects=" + detectedObjects +
                '}';
    }

}
