package org.makar.ocrapp.screenobjectsextractor.model.common;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;

import java.io.IOException;

// кастомный адаптер, потому что с 17 jdk доступ к внутренним полям через рефлексию ограничен, а стандартный адаптер почему-то не работает
// возможно причина в том, что я неправильно называл стандартные методы
public class TextObjectAdapter extends TypeAdapter<TextObject> {
    @Override
    public void write(JsonWriter out, TextObject value) throws IOException {
        out.beginObject();
        out.name("text").value(value.getText());
        out.name("x").value(value.getX());
        out.name("y").value(value.getY());
        out.name("width").value(value.getWidth());
        out.name("height").value(value.getHeight());
        out.endObject();
    }

    @Override
    public TextObject read(JsonReader in) throws IOException {
        TextObject obj = new TextObject();
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "text": obj.setText(in.nextString()); break;
                case "x": obj.setX(in.nextInt()); break;
                case "y": obj.setY(in.nextInt()); break;
                case "width": obj.setWidth(in.nextInt()); break;
                case "height": obj.setHeight(in.nextInt()); break;
            }
        }
        in.endObject();
        return obj;
    }
}
