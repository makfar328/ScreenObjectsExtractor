package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.objectdetection;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class PreProcessor {

    /**
     * ThreadLocal direct FloatBuffer: каждый поток создаёт буфер один раз
     * и переиспользует его для всех последующих вызовов.
     *
     * Почему ThreadLocal, а не static:
     *   - FloatBuffer не потокобезопасен — нельзя делить между потоками
     *   - ThreadLocal гарантирует: 1 буфер на поток, без конкуренции
     *
     * Почему direct, а не heap:
     *   - ONNX Runtime проверяет buffer.isDirect().
     *   - Если false → создаёт НОВЫЙ DirectByteBuffer внутри OrtUtil.prepareBuffer()
     *     при каждом вызове → утечка DirectMemory под нагрузкой (OutOfMemoryError)
     *   - Если true  → переиспользует наш буфер, новых allocateDirect() нет
     */
    private static final ThreadLocal<FloatBuffer> BUFFER_CACHE = new ThreadLocal<>();

    private static FloatBuffer acquireBuffer(int capacity) {
        FloatBuffer cached = BUFFER_CACHE.get();
        if (cached == null || cached.capacity() < capacity) {
            // Создаём direct-буфер с нативным порядком байт (требование ONNX Runtime)
            cached = ByteBuffer
                    .allocateDirect(capacity * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            BUFFER_CACHE.set(cached);
        }
        cached.clear();  // position=0, limit=capacity — готов к записи
        return cached;
    }

    public static OnnxTensor preprocessImage(
            BufferedImage originalImage,
            OrtEnvironment environment,
            long[] inputShape) throws OrtException {

        int inputHeight;
        int inputWidth;
        int inputChannels;
        if (inputShape.length == 4 && inputShape[0] == 1) {
            inputChannels = (int) inputShape[1];
            inputHeight   = (int) inputShape[2];
            inputWidth    = (int) inputShape[3];
        } else {
            throw new IllegalArgumentException("Onnx model has an invalid input shape");
        }

        // 1. Изменение размера
        BufferedImage resizedImage = new BufferedImage(inputWidth, inputHeight,
                BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, inputWidth, inputHeight, null);
        g.dispose();

        // 2. Нормализация → FloatBuffer (NCHW, BGR, 0-1)
        // БЫЛО: FloatBuffer.allocate(...)  ← heap-буфер → каждый вызов создаёт новый
        //       DirectByteBuffer внутри ONNX Runtime → OutOfMemoryError под нагрузкой
        // СТАЛО: переиспользуем ThreadLocal direct-буфер → 0 новых allocateDirect() на вызов
        int capacity = inputChannels * inputHeight * inputWidth;
        FloatBuffer floatBuffer = acquireBuffer(capacity);

        for (int c = 0; c < inputChannels; c++) {
            for (int y = 0; y < inputHeight; y++) {
                for (int x = 0; x < inputWidth; x++) {
                    int pixel = resizedImage.getRGB(x, y);
                    float value;
                    if (c == 0) {
                        value = (pixel & 0xFF) / 255.0f;          // Blue
                    } else if (c == 1) {
                        value = ((pixel >> 8) & 0xFF) / 255.0f;   // Green
                    } else {
                        value = ((pixel >> 16) & 0xFF) / 255.0f;  // Red
                    }
                    floatBuffer.put(value);
                }
            }
        }
        floatBuffer.flip();

        // 3. Создание OnnxTensor
        // ONNX Runtime видит isDirect()=true → использует floatBuffer напрямую,
        // НЕ выделяет новый DirectByteBuffer внутри OrtUtil.prepareBuffer()
        return OnnxTensor.createTensor(environment, floatBuffer, inputShape);
    }
}