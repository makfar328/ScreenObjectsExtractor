package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.ndarray.NDManager;
import org.junit.jupiter.api.*;

import org.junit.jupiter.api.Assertions.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для {@link YoloPostProcessor#postProcess}.
 *
 * <p>Предусловие: конструктор принимает инъектированный {@link NDManager}:
 * {@code new YoloPostProcessor(NDManager manager, int modelW, int modelH)}.
 * При инъекции {@code close()} процессора НЕ закрывает менеджер —
 * его жизненным циклом управляет тест.
 *
 * <p>Входной формат rawOutput: {@code float[1][84][numAnchors]},
 * где 84 = 4 (cx, cy, w, h) + 80 классов COCO.
 */
@DisplayName("YoloPostProcessor")
public class YoloPostProcessorTest {
    private static final int MODEL_W = 640;
    //private static final int MODEL_HEIGHT = 480;
    private static final int MODEL_H = 640;
    private static final float THRESHOLD = 0.25f;

    private NDManager manager;
    private YoloPostProcessor processor;

    @BeforeEach
    void setUp() {
        manager = NDManager.newBaseManager();
        processor = new YoloPostProcessor(MODEL_W, MODEL_H, manager);
    }

    @AfterEach
    void tearDown() {
        manager.close();
        processor.close();
    }

    /**
     * Формирует сырой вывод с <b>одним кандидатом</b>.
     *
     * @param cx        центр бокса по X (пространство модели, пиксели)
     * @param cy        центр бокса по Y
     * @param w         ширина бокса
     * @param h         высота бокса
     * @param score     уверенность для указанного класса
     * @param classIdx  индекс класса (0..79)
     */
    private float[][][] singleAnchor(float cx, float cy, float w, float h,
                                     float score, int classIdx) {
        float[][][] raw = new float[1][84][1];
        raw[0][0][0] = cx;
        raw[0][1][0] = cy;
        raw[0][2][0] = w;
        raw[0][3][0] = h;
        raw[0][4 + classIdx][0] = score;
        return raw;
    }

    private float[][][] twoAnchors(float cx1, float cy1, float w1, float h1,
                                   float score1, int classIdx1,
                                   float cx2, float cy2, float w2, float h2,
                                   float score2, int classIdx2) {
        float[][][] raw = new float[1][84][2];

        raw[0][0][0] = cx1;  raw[0][1][0] = cy1;  raw[0][2][0] = w1;  raw[0][3][0] = h1;
        raw[0][4 + classIdx1][0] = score1;

        raw[0][0][1] = cx2;  raw[0][1][1] = cy2;  raw[0][2][1] = w2;  raw[0][3][1] = h2;
        raw[0][4 + classIdx2][1] = score2;

        return raw;
    }


    @Nested
    @DisplayName("Фильтрация по порогу уверенности")
    class ConfidenceFilteringTests {

        @Test
        @DisplayName("Сценарий 1: confidence=0.35f > порог → 1 объект в результате")
        void aboveThreshold_oneObject() {
            float[][][] raw = singleAnchor(320,320,100,100,0.35f,0);
            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());
        }
        @Test
        @DisplayName("Сценарий 2: confidence=0.24f < порог → 0 объектов")
        void belowThreshold_noObjects() {
            float[][][] raw = singleAnchor(320, 320, 100, 100, 0.24f, 0);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(0, result.getNumberOfObjects());
        }

        @Test
        @DisplayName("Сценарий 3: confidence=0.25f == порог → 0 объектов (строгий >)")
        void exactThreshold_filteredOut() {
            float[][][] raw = singleAnchor(320, 320, 100, 100, THRESHOLD, 0);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(0, result.getNumberOfObjects(),
                    "Порог строгий (>): ровно 0.25f должно быть отфильтровано");
        }

        @Test
        @DisplayName("Сценарий 4: все нули → 0 объектов, нет исключений")
        void allZeros_noObjectsNoException() {
            float[][][] raw = new float[1][84][1]; // Java инициализирует нулями

            DetectedObjects result = assertDoesNotThrow(
                    () -> processor.postProcess(raw, MODEL_W, MODEL_H));

            assertNotNull(result);
            assertEquals(0, result.getNumberOfObjects());
        }

        @Test
        @DisplayName("Сценарий 5: несколько кандидатов — часть выше, часть ниже порога")
        void mixedConfidences_onlyAboveThresholdSurvive() {
            // 3 кандидата: 0.10f (ниже), 0.30f (выше), 0.20f (ниже)
            float[][][] raw = new float[1][84][3];

            // Кандидат 0 — разные позиции, чтобы NMS не срабатывал
            raw[0][0][0] = 100f; raw[0][1][0] = 100f;
            raw[0][2][0] = 50f;  raw[0][3][0] = 50f;
            raw[0][4][0] = 0.10f; // class 0 → отфильтруется

            // Кандидат 1
            raw[0][0][1] = 320f; raw[0][1][1] = 320f;
            raw[0][2][1] = 50f;  raw[0][3][1] = 50f;
            raw[0][4 + 1][1] = 0.30f; // class 1 → пройдёт (другой класс и позиция)

            // Кандидат 2
            raw[0][0][2] = 500f; raw[0][1][2] = 500f;
            raw[0][2][2] = 50f;  raw[0][3][2] = 50f;
            raw[0][4 + 2][2] = 0.20f; // class 2 → отфильтруется

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects(),
                    "Только кандидат с 0.30f должен пройти фильтр");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Группа 2: Non-Maximum Suppression (NMS)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Non-Maximum Suppression")
    class NmsTests {

        @Test
        @DisplayName("Сценарий 6: два сильно перекрывающихся бокса одного класса → 1 объект с наибольшим confidence")
        void highIou_sameClass_onlyHigherConfidenceSurvives() {
            // Боксы почти идентичны: центры рядом, большое перекрытие → IoU >> 0.45
            // Первый: confidence=0.9, второй: confidence=0.7 — оба класс 0
            float[][][] raw = twoAnchors(
                    320f, 320f, 200f, 200f, 0.9f, 0,
                    330f, 330f, 200f, 200f, 0.7f, 0
            );

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects(),
                    "NMS должен подавить второй бокс при высоком IoU одного класса");
            assertEquals(0.9f, result.item(0).getProbability(), 0.05,
                    "Выживший бокс должен иметь наибольший confidence = 0.9");
        }

        @Test
        @DisplayName("Сценарий 7: два слабо перекрывающихся бокса одного класса → 2 объекта")
        void lowIou_sameClass_bothSurvive() {
            // Боксы далеко друг от друга: IoU ≈ 0
            // якорь 0: центр (150, 150), 100×100 → x1=100, y1=100, x2=200, y2=200
            // якорь 1: центр (450, 450), 100×100 → x1=400, y1=400, x2=500, y2=500
            float[][][] raw = twoAnchors(
                    150f, 150f, 100f, 100f, 0.9f, 0,
                    450f, 450f, 100f, 100f, 0.8f, 0
            );

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(2, result.getNumberOfObjects(),
                    "При низком IoU NMS не должен подавлять боксы одного класса");
        }

        @Test
        @DisplayName("Сценарий 8: два сильно перекрывающихся бокса разных классов → 2 объекта")
        void highIou_differentClasses_bothSurvive() {
            // Почти идентичные боксы, но разные классы: class 0 и class 1
            float[][][] raw = twoAnchors(
                    320f, 320f, 200f, 200f, 0.9f, 0,
                    325f, 325f, 200f, 200f, 0.85f, 1
            );

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(2, result.getNumberOfObjects(),
                    "NMS не применяется к боксам разных классов");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Группа 3: Определение класса и имени
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Определение класса объекта")
    class ClassDetectionTests {

        @Test
        @DisplayName("Сценарий 11: classId=0 → имя класса 'person'")
        void classId0_nameIsPerson() {
            float[][][] raw = singleAnchor(320, 320, 100, 100, 0.9f, 0);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());
            assertEquals("person", result.item(0).getClassName());
        }

        @Test
        @DisplayName("Сценарий 12: classId=2 → имя класса 'car'")
        void classId2_nameIsCar() {
            float[][][] raw = singleAnchor(320, 320, 100, 100, 0.9f, 2);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());
            assertEquals("car", result.item(0).getClassName());
        }

        @Test
        @DisplayName("Сценарий 13: classId=79 → имя класса 'toothbrush' (последний)")
        void classId79_nameIsToothbrush() {
            float[][][] raw = singleAnchor(320, 320, 100, 100, 0.9f, 79);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());
            assertEquals("toothbrush", result.item(0).getClassName());
        }

        @Test
        @DisplayName("Сценарий 14: class[10]=0.3f, class[20]=0.8f → имя класса 20, confidence≈0.8f")
        void twoClassScores_highestWins() {
            float[][][] raw = new float[1][84][1];
            raw[0][0][0] = 320f;
            raw[0][1][0] = 320f;
            raw[0][2][0] = 100f;
            raw[0][3][0] = 100f;
            raw[0][4 + 10][0] = 0.3f;  // класс 10 — проигрывает
            raw[0][4 + 20][0] = 0.8f;  // класс 20 — побеждает

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());

            List<String> classNames = YoloPostProcessor.getClassNames();
            assertEquals(classNames.get(20), result.item(0).getClassName(),
                    "Должен победить класс с индексом 20 (score 0.8 > 0.3)");
            assertEquals(0.8f, result.item(0).getProbability(), 0.05,
                    "Уверенность результата должна соответствовать выигравшему классу");
        }

        @Test
        @DisplayName("Сценарий 15: confidence=0.77f → result.getProbability() ≈ 0.77f")
        void resultProbability_matchesInputConfidence() {
            float inputConfidence = 0.77f;
            float[][][] raw = singleAnchor(320, 320, 100, 100, inputConfidence, 5);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());
            assertEquals(inputConfidence, result.item(0).getProbability(), 0.01,
                    "Уверенность в результате должна точно соответствовать входной");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Группа 4: Масштабирование и нормализация координат
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Масштабирование и нормализация координат")
    class CoordinateScalingTests {

        @Test
        @DisplayName("Сценарий 17: апскейл модель 640×640 → оригинал 1280×720, центр бокса ≈ (0.5, 0.5)")
        void upscale_centerBoxNormalizedToHalf() {
            // Центр бокса в пространстве модели: cx=320, cy=320 (ровно середина 640×640)
            // После масштабирования и нормализации → (320/640 = 0.5f, 320/640 = 0.5f)
            // Ширина и высота бокса — небольшие, чтобы не выйти за границу
            float[][][] raw = singleAnchor(320f, 320f, 40f, 40f, 0.9f, 0);

            // Оригинальный размер изображения отличается от размера входа модели
            DetectedObjects result = processor.postProcess(raw, 1280, 720);

            assertEquals(1, result.getNumberOfObjects());

            DetectedObjects.DetectedObject item = (DetectedObjects.DetectedObject) result.items().get(0);
            Rectangle rect = (Rectangle) item.getBoundingBox();

            assertTrue(rect.getX() >= 0.0 && rect.getX() <= 1.0);
            assertTrue(rect.getY() >= 0.0 && rect.getY() <= 1.0);
            assertTrue(rect.getWidth() > 0.0);
            assertTrue(rect.getHeight() > 0.0);

            // cx_norm = (x1_norm + width_norm / 2)
            //         = normalizedX1 + normalizedWidth / 2
            //         = (cx - w/2) / modelW + (w / modelW) / 2
            //         = cx / modelW = 320 / 640 = 0.5
            //
            // Аналогично cy_norm = cy / modelH = 320 / 640 = 0.5
            double centerX = rect.getX() + rect.getWidth()  / 2.0;
            double centerY = rect.getY() + rect.getHeight() / 2.0;

            assertEquals(0.5, centerX, 0.01,
                    "Нормализованный центр по X должен быть ≈ 0.5 независимо от originalImageWidth");
            assertEquals(0.5, centerY, 0.01,
                    "Нормализованный центр по Y должен быть ≈ 0.5 независимо от originalImageHeight");
        }

        @Test
        @DisplayName("Сценарий 18: все координаты результата лежат в диапазоне [0.0, 1.0]")
        void allCoordinatesWithinNormalizedRange() {
            // Произвольный бокс внутри поля модели 640×640
            // cx=480, cy=200, w=150, h=80 → x1=405, y1=160, x2=555, y2=240 (все в пределах 640)
            float[][][] raw = singleAnchor(480f, 200f, 150f, 80f, 0.9f, 3);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());

            DetectedObjects.DetectedObject item = (DetectedObjects.DetectedObject) result.items().get(0);
            Rectangle rect = (Rectangle) item.getBoundingBox();

            double x = rect.getX();
            double y = rect.getY();
            double w = rect.getWidth();
            double h = rect.getHeight();

            assertAll("Все нормализованные координаты должны быть в [0.0, 1.0]",
                    () -> assertTrue(x >= 0.0, "x должен быть ≥ 0, но: " + x),
                    () -> assertTrue(y >= 0.0, "y должен быть ≥ 0, но: " + y),
                    () -> assertTrue(w >= 0.0, "width должен быть ≥ 0, но: " + w),
                    () -> assertTrue(h >= 0.0, "height должен быть ≥ 0, но: " + h),
                    () -> assertTrue(x + w <= 1.0, "x + width не должен превышать 1.0, но: " + (x + w)),
                    () -> assertTrue(y + h <= 1.0, "y + height не должен превышать 1.0, но: " + (y + h))
            );
        }

        @Test
        @DisplayName("Сценарий 19: бокс у левого верхнего края (cx=5, cy=5, w=10, h=10) → x1=0, нет отрицательных координат")
        void cornerBox_noNegativeCoordinates() {
            // cx=5, cy=5, w=10, h=10
            // x1 = cx - w/2 = 5 - 5 = 0  →  normalizedX1 = 0 / 640 = 0.0
            // y1 = cy - h/2 = 5 - 5 = 0  →  normalizedY1 = 0 / 640 = 0.0
            float[][][] raw = singleAnchor(5f, 5f, 10f, 10f, 0.9f, 0);

            DetectedObjects result = processor.postProcess(raw, MODEL_W, MODEL_H);

            assertEquals(1, result.getNumberOfObjects());

            DetectedObjects.DetectedObject item = (DetectedObjects.DetectedObject) result.items().get(0);
            Rectangle rect = (Rectangle) item.getBoundingBox();

            double x = rect.getX();
            double y = rect.getY();

            assertAll("Координаты бокса у края не должны быть отрицательными",
                    () -> assertTrue(x >= 0.0, "normalizedX1 должен быть ≥ 0, но: " + x),
                    () -> assertTrue(y >= 0.0, "normalizedY1 должен быть ≥ 0, но: " + y),
                    () -> assertEquals(0.0, x, 0.001,
                            "normalizedX1 должен быть ≈ 0.0 для бокса вплотную к левому краю"),
                    () -> assertEquals(0.0, y, 0.001,
                            "normalizedY1 должен быть ≈ 0.0 для бокса вплотную к верхнему краю")
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Группа 6: Некорректный ввод
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Некорректный ввод")
    class InvalidInputTests {

        @Test
        @DisplayName("Сценарий 24: rawOutput не является float[][][] → ClassCastException")
        void wrongInputType_throwsClassCastException() {
            // Передаём int[][] вместо float[][][] — явное нарушение контракта метода
            int[][] wrongType = new int[84][1];

            assertThrows(ClassCastException.class,
                    () -> processor.postProcess(wrongType, MODEL_W, MODEL_H),
                    "Передача неверного типа должна вызывать ClassCastException");
        }
    }
}
