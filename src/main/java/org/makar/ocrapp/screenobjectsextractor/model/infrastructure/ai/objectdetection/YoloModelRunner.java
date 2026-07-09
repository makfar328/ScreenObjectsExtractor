package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.onnxruntime.*;
//import com.microsoft.onnxruntime.*;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class YoloModelRunner implements IModelRunner {

    private static final Logger logger = Logger.getLogger(YoloModelRunner.class.getName()); // ?

    private OrtSession session;
    private OrtEnvironment environment;
    private String inputName;   // имя входного узла
    private long[] inputShape;  // форма входного узла

    private int inputWidth;       // Ширина изображения, ожидаемая моделью
    private int inputHeight;      // Высота изображения, ожидаемая моделью
    private int inputChannels;    // Количество каналов изображения, ожидаемое моделью
    //String modelPath = "C:/Users/Makar/IdeaProjects/java-project/TryOnnx2/yolo11s.onnx";

    public int getInputWidth() {
        return inputWidth;
    }

    public int getInputHeight() {
        return inputHeight;
    }

    public YoloModelRunner(String modelPath) throws OrtException {

        environment = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.addCPU(true);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        try {

            session = environment.createSession(modelPath, options);

            Map<String, NodeInfo> modelInputInfo = session.getInputInfo();
            /* получаю карту входных узлов модели, чтобы
            проверить адекватность модели, выявить ошибки и их обработать,
            получить имя входного узла, это нужно, чтобы подавать на него коллекцию <Map<String, Tensor>> */
            if (modelInputInfo == null || modelInputInfo.isEmpty()) {
                throw new IllegalArgumentException("ONNX model has no inputs. null or createEmpty inputs.");
            }

            /*logger.info("--Input Info for--" + modelPath + "----");
            for (Map.Entry<String, NodeInfo> entry: modeleInputInfo.entrySet()) {
                NodeInfo nodeInfo = entry.getValue();
                logger.info(" Input Name:" + entry.getKey());
                logger.info("  Node Info: " + nodeInfo);

                if (nodeInfo.getInfo() instanceof TensorInfo) {
                // 1. используется для проверки, является ли объект экземпляром конкретного класса,
                // подкласса или реализует ли он определенный интерфейс. Он возвращает `true`,
                // если объект соответствует типу, и `false` в противном случае
                    TensorInfo tensorInfo = (TensorInfo) nodeInfo.getInfo();
                    logger.info("  Tensor Info: " + tensorInfo);
                }
            }*/

            this.inputName = modelInputInfo.keySet().iterator().next(); // Мапа -> коллекция ключей -> итератор() -> следующий элемент
            TensorInfo firstInputTensorInfo = (TensorInfo) modelInputInfo.get(this.inputName).getInfo();
            this.inputShape = firstInputTensorInfo.getShape();

            if (inputShape.length == 4 && inputShape[0] == 1) {
                this.inputChannels = (int) inputShape[1];
                this.inputHeight = (int) inputShape[2];
                this.inputWidth = (int) inputShape[3];
            } else {
                throw new IllegalArgumentException("Onnx model has an invalid input shape");
            }


        } catch (NullPointerException e) {
            System.out.println("Null Pointer Exception");
        }
    }

    public OrtSession.Result runModel(BufferedImage image) throws OrtException {
        logger.info("Running ONNX model inference...");
        try (OnnxTensor inputTensor = PreProcessor.preprocessImage(image, environment, inputShape)) {
            // Выполняем инференс, передавая входной тензор под именем, полученным из модели.
            return session.run(Collections.singletonMap(inputName, inputTensor));
        }
    }

    public void outInputInfo() throws OrtException {
        System.out.println("Running onnx model (initialization complete)");
        System.out.println("Input Info for the model:");

        Map<String, NodeInfo> inputInfo = session.getInputInfo();
        inputInfo.forEach((key, nodeInfo) -> {
            System.out.println("\tInput Name: " + key);
            System.out.println("\t  Node Info: " + nodeInfo.toString()); // Общая информация об узле

            // Чтобы получить более детальную информацию о форме и типе данных,
            // нужно привести NodeInfo.getInfo() к TensorInfo
            if (nodeInfo.getInfo() instanceof TensorInfo) {
                TensorInfo tensorInfo = (TensorInfo) nodeInfo.getInfo();
                System.out.println("\t  Data Type: " + tensorInfo.type);
                System.out.println("\t  Shape: " + Arrays.toString(tensorInfo.getShape()));
            } else {
                System.out.println("\t  Info Type: " + nodeInfo.getInfo().getClass().getSimpleName() + " (not a TensorInfo)");
            }
        });
    }

    public Map<String, NodeInfo> getInputInfo() throws OrtException {
        return Collections.unmodifiableMap(session.getInputInfo());
    }

    /*public OnnxTensor preprocessImage(BufferedImage originalImage) throws OrtException {
        // 1. Изменение размера изображения до ожидаемого моделью
        // Создаем BufferedImage в формате BGR (TYPE_3BYTE_BGR), который часто ожидается YOLO
        BufferedImage resizedImage = new BufferedImage(inputWidth, inputHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, inputWidth, inputHeight, null);
        g.dispose();

        // 2. Нормализация пикселей (0-1) и заполнение FloatBuffer в NCHW формате
        // Для YOLO моделей часто используется BGR порядок каналов и нормализация 0-1.
        FloatBuffer floatBuffer = FloatBuffer.allocate(inputWidth * inputHeight * inputChannels);

        // Заполнение FloatBuffer в порядке NCHW, BGR (Channels, Height, Width)
        for (int c = 0; c < inputChannels; c++) { // Итерация по каналам (0=B, 1=G, 2=R)
            for (int y = 0; y < inputHeight; y++) { // Итерация по высоте
                for (int x = 0; x < inputWidth; x++) { // Итерация по ширине
                    int pixel = resizedImage.getRGB(x, y); // Получаем RGB пикселя (int, формат ARGB)

                    // Извлекаем компоненты BGR
                    // B: (pixel) & 0xFF
                    // G: (pixel >> 8) & 0xFF
                    // R: (pixel >> 16) & 0xFF

                    float value;
                    if (c == 0) { // Blue channel
                        value = (float) ((pixel) & 0xFF) / 255.0f;
                    } else if (c == 1) { // Green channel
                        value = (float) ((pixel >> 8) & 0xFF) / 255.0f;
                    } else { // Red channel
                        value = (float) ((pixel >> 16) & 0xFF) / 255.0f;
                    }
                    floatBuffer.put(value);
                }
            }
        }
        floatBuffer.flip(); // Переключение FloatBuffer в режим чтения

        // 3. Создание OnnxTensor
        // Используем environment из OnnxModelRunner
        return OnnxTensor.createTensor(environment, floatBuffer, inputShape);
    }*/

    @Override
    public void close() {
        if (session != null) {
            try {
                session.close();
                session = null;
            } catch (OrtException e) {
                throw new RuntimeException("error while closing session: " + e.getMessage());
            }
        }
        if (environment != null) {
            environment.close();
            environment = null; // вроде как здесь OrtException никогда не появится
        }
    }
}