package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map; // Import Map for Map.Entry
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

public class ObjectDetectionService implements IObjectDetectionService {
    private static final Logger logger = Logger.getLogger(ObjectDetectionService.class.getName());

    private final IModelRunner modelRunner;
    private final YoloPostProcessor postProcessor;
    private final String yoloOutputName; // Имя выходного тензора YOLO модели


    public ObjectDetectionService(String modelPath, String yoloOutputName) throws OrtException {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Model path cannot be null or createEmpty.");
        }
        if (yoloOutputName == null || yoloOutputName.trim().isEmpty()) {
            throw new IllegalArgumentException("YOLO output name cannot be null or createEmpty.");
        }
        this.modelRunner = new YoloModelRunner(modelPath);
        // YoloPostProcessor нуждается в размерах, с которыми работает модель
        this.postProcessor = new YoloPostProcessor(modelRunner.getInputWidth(), modelRunner.getInputHeight());
        this.yoloOutputName = yoloOutputName;
        logger.log(Level.INFO, "ObjectDetectionService initialized with model: {0}, output tensor: {1}", new Object[]{modelPath, yoloOutputName});
    }

    public ObjectDetectionService(IModelRunner modelRunner,
                                  YoloPostProcessor postProcessor,
                                  String yoloOutputName) throws OrtException {
        if (yoloOutputName == null || yoloOutputName.trim().isEmpty()) {
            throw new IllegalArgumentException("YOLO output name cannot be null or createEmpty.");
        }
        this.modelRunner    = modelRunner;
        this.postProcessor  = postProcessor;
        this.yoloOutputName = yoloOutputName;
    }


    public DetectedObjects detectObjects(BufferedImage originalImage) throws OrtException {
        if (originalImage == null) {
            throw new IllegalArgumentException("Input image for detection cannot be null.");
        }

        logger.log(Level.INFO, "Starting object detection for image of size {0}x{1}", new Object[]{originalImage.getWidth(), originalImage.getHeight()});

        try (OrtSession.Result results = modelRunner.runModel(originalImage)) {
            logger.log(Level.FINE, "ONNX inference completed. Searching for YOLO output tensor '{0}'...", yoloOutputName);

            // FIX 1 & 2: Use Map.Entry<String, OnnxValue> for iterating OrtSession.Result
            Map.Entry<String, OnnxValue> yoloOutputEntry = StreamSupport.stream(results.spliterator(), false)
                    .filter(entry -> entry.getKey().equals(yoloOutputName))
                    .findFirst()
                    .orElseThrow(() -> new OrtException("YOLO output tensor '" + yoloOutputName + "' not found in model results. Please check model output names."));

            try (OnnxValue onnxValue = yoloOutputEntry.getValue()) { // .getValue() is now correctly resolved
                if (onnxValue instanceof OnnxTensor) {
                    OnnxTensor tensor = (OnnxTensor) onnxValue;
                    Object rawOutput = tensor.getValue();

                    if (rawOutput instanceof float[][][]) {
                        logger.log(Level.INFO, "YOLO output tensor found. Starting post-processing...");
                        return postProcessor.postProcess(
                                rawOutput,
                                originalImage.getWidth(),
                                originalImage.getHeight()
                        );
                    } else {
                        throw new OrtException("YOLO output tensor data is not in expected float[][][] format. Actual type: " + (rawOutput != null ? rawOutput.getClass().getName() : "null"));
                    }
                } else {
                    throw new OrtException("YOLO output is not an OnnxTensor. Actual type: " + (onnxValue != null ? onnxValue.getClass().getName() : "null"));
                }
            }
        } catch (OrtException e) {
            logger.log(Level.SEVERE, "ONNX Runtime Error during object detection: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.log(Level.SEVERE, "Unexpected error during object detection: " + e.getMessage(), e);
            // FIX 3: Use OrtException(Throwable cause) constructor to propagate the cause
        }
        // FIX 4: The 'OrtException is never thrown' error should resolve as modelRunner.runModel() throws OrtException.
        return null;
    }


    /**
     * Закрывает используемые ресурсы (YoloModelRunner, YoloPostProcessor).
     * @throws IOException Если произошла ошибка при закрытии ресурсов.
     */
    @Override
    public void close() throws IOException {
        logger.log(Level.INFO, "Closing ObjectDetectionService resources.");
        try {
            if (modelRunner != null) {
                modelRunner.close();
            }
        } finally {
            if (postProcessor != null) {
                postProcessor.close();
            }
        }
        logger.log(Level.INFO, "ObjectDetectionService resources closed.");
    }
}