Проект извлечения объектов с экрана с помощью YOLO.

Проект не полный : моделей в репозитории НЕТ

Модели YOLO (`yolo11x.onnx`, `yolo26x.onnx`) **не загружены в Git** из‑за лимита GitHub в 100 МБ на файл.

Как запустить проект

1. Скачайте модели: [https://github.com/ultralytics/ultralytics].
2. Положите их в: `src/main/resources/models/`.
3. Соберите проект: `mvn clean package`.
4. Запустите: `java -jar target/ScreenObjectsExtractor-1.0-SNAPSHOT.jar` или через IDE.
