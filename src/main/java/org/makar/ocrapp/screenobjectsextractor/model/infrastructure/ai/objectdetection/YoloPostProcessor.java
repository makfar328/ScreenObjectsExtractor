package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
// import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class YoloPostProcessor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(YoloPostProcessor.class);

    // Эти параметры зависят от вашей модели YOLO и её конфигурации
    private static final int NUM_CLASSES = 80; // Например, 80 для COCO
    private static final float CONF_THRESHOLD = 0.25f; // Порог уверенности объекта
    private static final float IOU_THRESHOLD = 0.45f; // Порог Intersection over Union для NMS

    private final NDManager manager; // NDManager для создания и управления NDArray
    private final int modelInputWidth; // Ширина изображения, которое подается в модель (640)
    private final int modelInputHeight; // Высота изображения, которое подается в модель (640)

    // Список имен классов, например для COCO.
    // Вы должны заменить это на реальные классы вашей модели
    private static final List<String> CLASS_NAMES = Arrays.asList(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
            "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
            "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush");

    public static List<String> getClassNames() {
        return CLASS_NAMES;
    }

/*    public YoloPostProcessor(int modelInputWidth, int modelInputHeight) {
        // Создаем менеджер для NDArray. Используйте manager.newBaseManager() если это не основной поток.
        this.manager = NDManager.newBaseManager();
        this.modelInputWidth = modelInputWidth;
        this.modelInputHeight = modelInputHeight;
    }*/

    public YoloPostProcessor(int modelInputWidth, int modelInputHeight, NDManager manager) {
        this.manager = manager;
        this.modelInputWidth = modelInputWidth;
        this.modelInputHeight = modelInputHeight;
    }

    public YoloPostProcessor(int modelInputWidth, int modelInputHeight) {
        this(modelInputWidth, modelInputHeight, NDManager.newBaseManager());
    }


    /**
     * Выполняет постобработку необработанного выходного тензора YOLO.
     *
     * @param rawOutput         Необработанный выходной тензор в виде float[][][].
     * @param originalImageWidth  Исходная ширина изображения.
     * @param originalImageHeight Исходная высота изображения.
     * @return Объект DetectedObjects, содержащий обнаруженные рамки, классы и уверенность.
     */
    public DetectedObjects postProcess(Object rawOutput, int originalImageWidth, int originalImageHeight) {
        // 1. Преобразование Object (float[][][]) в NDArray
        // Формат выходного тензора YOLOv8: [1, 84, 8400]
        float[][][] outputArray = (float[][][]) rawOutput;

        // Определяем ожидаемую форму NDArray
        Shape outputShape = new Shape(outputArray.length, outputArray[0].length, outputArray[0][0].length);

        // Выравниваем 3D массив в 1D массив
        float[] flattenedOutput = new float[(int) outputShape.size()];
        int index = 0;
        for (int i = 0; i < outputArray.length; i++) {
            for (int j = 0; j < outputArray[i].length; j++) {
                for (int k = 0; k < outputArray[i][j].length; k++) {
                    flattenedOutput[index++] = outputArray[i][j][k];
                }
            }
        }

        // Создаем NDArray из выровненного массива и заданной формы
        NDArray outputTensor = manager.create(flattenedOutput, outputShape);


        // Меняем на [1, 8400, 84] для удобства обработки: Batch, Num_Boxes, Attributes
        outputTensor = outputTensor.transpose(0, 2, 1); // [1, 8400, 84]

        // Удаляем размерность батча, если она 1
        NDArray detections = outputTensor.squeeze(0); // [8400, 84]

        // 2. Извлечение координат боксов (x, y, w, h) и вероятностей классов
        // detections: [num_proposals, 4 (bbox) + num_classes (class_confidences)]
        NDArray boxes = detections.get(":,0:4"); // ИСПРАВЛЕНО: Используем прямую строковую индексацию
        NDArray classProbs = detections.get(String.format(":,4:%d", 4 + NUM_CLASSES)); // ИСПРАВЛЕНО: Используем String.format

        // 3. Преобразование x,y,w,h (центр, ширина, высота) в x1,y1,x2,y2 (левый верхний, правый нижний)
        // Координаты уже в пикселях на входе 640x640
        // ИСПРАВЛЕНО: Используем прямую строковую индексацию
        NDArray x1 = boxes.get(":,0").sub(boxes.get(":,2").div(2)); // x - w/2
        NDArray y1 = boxes.get(":,1").sub(boxes.get(":,3").div(2)); // y - h/2
        NDArray x2 = boxes.get(":,0").add(boxes.get(":,2").div(2)); // x + w/2
        NDArray y2 = boxes.get(":,1").add(boxes.get(":,3").div(2)); // y + h/2

        // ИСПРАВЛЕНО: Правильное использование manager.stack()
        //NDList boxCoordinates = new NDList(x1, y1, x2, y2);
        //boxes = x1.stack(y1, 1).stack(x2, 1).stack(y2, 1);

        // Предполагаем, что x1, y1, x2, y2 все имеют форму (8400,)

        // Шаг 1: Расширяем каждое одномерное NDArray до двумерного столбца (форма (8400, 1))
        NDArray x1_col = x1.expandDims(1); // Теперь x1_col имеет форму (8400, 1)
        NDArray y1_col = y1.expandDims(1); // Теперь y1_col имеет форму (8400, 1)
        NDArray x2_col = x2.expandDims(1); // Теперь x2_col имеет форму (8400, 1)
        NDArray y2_col = y2.expandDims(1); // Теперь y2_col имеет форму (8400, 1)

        // Шаг 2: Объединяем эти столбцы с помощью concat по оси 1
        // Метод concat объединяет массивы вдоль существующей оси.
        // Для успешной конкатенации, все размеры, кроме той, по которой происходит объединение, должны совпадать.
        // В нашем случае, размер по оси 0 (8400) совпадает, а по оси 1 мы их объединяем.
        boxes = x1_col.concat(y1_col, 1) // Результат: (8400, 2)
                .concat(x2_col, 1) // Результат: (8400, 3)
                .concat(y2_col, 1); // Конечный результат: (8400, 4)

        // NDArray boxes теперь будет иметь желаемую форму [8400, 4] (x1, y1, x2, y2)

        // 4. Получение максимальной вероятности для каждого класса и соответствующего индекса
        // В YOLOv8 выходные classProbs уже содержат доверительную оценку, нет отдельного objectness score
        NDArray confidences = classProbs.max(new int[]{1}); // Максимальная вероятность класса для каждого бокса [8400]
        NDArray classIds = classProbs.argMax(1).toType(DataType.INT32, false); // Индекс класса для каждого бокса [8400]

        // 5. Фильтрация по порогу уверенности
        List<Prediction> rawPredictions = new ArrayList<>();
        for (int i = 0; i < (int) confidences.size(); i++) {
            float currentConfidence = confidences.getFloat(i); // ИСПРАВЛЕНО: Переименована переменная
            if (currentConfidence > CONF_THRESHOLD) {
                float boxX1 = boxes.getFloat(i, 0);
                float boxY1 = boxes.getFloat(i, 1);
                float boxX2 = boxes.getFloat(i, 2);
                float boxY2 = boxes.getFloat(i, 3);
                int classId = classIds.getInt(i);

                rawPredictions.add(new Prediction(boxX1, boxY1, boxX2, boxY2, currentConfidence, CLASS_NAMES.get(classId), classId));
            }
        }

        // 6. Применение Non-Maximum Suppression (NMS)
        // Сортируем по уверенности в убывающем порядке
        //rawPredictions.sort(Comparator.comparingDouble(p -> p.confidence).reversed());
        rawPredictions.sort(Comparator.comparingDouble(Prediction::getConfidence).reversed());

        List<Prediction> nmsPredictions = new ArrayList<>();
        boolean[] suppressed = new boolean[rawPredictions.size()]; // Массив для отслеживания подавленных рамок

        for (int i = 0; i < rawPredictions.size(); i++) {
            if (suppressed[i]) continue;

            Prediction current = rawPredictions.get(i);
            nmsPredictions.add(current);

            for (int j = i + 1; j < rawPredictions.size(); j++) {
                if (suppressed[j]) continue;

                Prediction next = rawPredictions.get(j);
                // Применяем NMS только если классы совпадают
                if (current.classId == next.classId && calculateIoU(current, next) > IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }

        List<Float> finalScoresFloat = new ArrayList<>();
        List<String> finalClassNames = new ArrayList<>();
        List<BoundingBox> finalBoundingBoxes = new ArrayList<>();

        // 7. Масштабирование координат обратно к оригинальному размеру изображения
        for (Prediction p : nmsPredictions) {
            float scaleX = (float) originalImageWidth / modelInputWidth;
            float scaleY = (float) originalImageHeight / modelInputHeight;

            // Координаты уже в пикселях (на масштабе 640х640), нужно только масштабировать
            float scaledX1 = p.x1 * scaleX; // ИСПРАВЛЕНО: Переименованы переменные
            float scaledY1 = p.y1 * scaleY; // ИСПРАВЛЕНО: Переименованы переменные
            float scaledX2 = p.x2 * scaleX; // ИСПРАВЛЕНО: Переименованы переменные
            float scaledY2 = p.y2 * scaleY; // ИСПРАВЛЕНО: Переименованы переменные

            // DJL Rectangle ожидает нормализованные координаты (0-1) относительно исходного изображения
            float normalizedX1 = scaledX1 / originalImageWidth;
            float normalizedY1 = scaledY1 / originalImageHeight;
            float normalizedWidth = (scaledX2 - scaledX1) / originalImageWidth;
            float normalizedHeight = (scaledY2 - scaledY1) / originalImageHeight;

            Rectangle rect = new Rectangle(normalizedX1, normalizedY1, normalizedWidth, normalizedHeight);

            finalScoresFloat.add(p.confidence);
            finalClassNames.add(p.className);
            finalBoundingBoxes.add(rect);
        }

        List<Double> finalScoresDouble = finalScoresFloat.stream()
                .map(Float::doubleValue)
                .collect(Collectors.toList());

        // Возвращаем результат в формате DJL
        return new DetectedObjects(finalClassNames, finalScoresDouble, finalBoundingBoxes);
    }

    /**
     * Вычисляет Intersection over Union (IoU) для двух ограничивающих рамок.
     */
    private float calculateIoU(Prediction box1, Prediction box2) {
        float x1 = Math.max(box1.x1, box2.x1);
        float y1 = Math.max(box1.y1, box2.y1);
        float x2 = Math.min(box1.x2, box2.x2);
        float y2 = Math.min(box1.y2, box2.y2);

        float intersectionWidth = Math.max(0, x2 - x1);
        float intersectionHeight = Math.max(0, y2 - y1);
        float intersectionArea = intersectionWidth * intersectionHeight;

        float box1Width = box1.x2 - box1.x1;
        float box1Height = box1.y2 - box1.y1;
        float box1Area = box1Width * box1Height;

        float box2Width = box2.x2 - box2.x1;
        float box2Height = box2.y2 - box2.y1;
        float box2Area = box2Width * box2Height;

        float unionArea = box1Area + box2Area - intersectionArea;
        if (unionArea <= 0) return 0; // Избегаем деления на ноль
        return intersectionArea / unionArea;
    }

    // Вспомогательный класс для хранения предсказаний перед NMS
    private static class Prediction {
        float x1, y1, x2, y2;
        float confidence;
        String className;
        int classId;

        Prediction(float x1, float y1, float x2, float y2, float confidence, String className, int classId) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.confidence = confidence;
            this.className = className;
            this.classId = classId;
        }

        public float getConfidence() {
            return confidence;
        }
    }

    @Override
    public void close() {
        if (manager != null) {
            manager.close();
        }
        logger.info("YoloPostProcessor closed NDManager.");
    }
}
