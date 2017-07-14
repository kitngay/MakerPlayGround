package io.makerplayground.project;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.makerplayground.device.Parameter;
import io.makerplayground.device.Value;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import javax.swing.text.html.parser.Entity;
import java.io.IOException;
import java.util.Map;

/**
 * Created by USER on 13-Jul-17.
 */
public class UserSettingSerializer extends StdSerializer<UserSetting> {
    public UserSettingSerializer() {
        this(null);
    }

    public UserSettingSerializer(Class<UserSetting> t) {
        super(t);
    }

    @Override
    public void serialize(UserSetting userSetting, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("device", userSetting.getDevice().getName());
        jsonGenerator.writeStringField("action", userSetting.getAction().getName());

        jsonGenerator.writeArrayFieldStart("valueMap");
        for (Map.Entry<Parameter, Object> v : userSetting.getValueMap().entrySet()) {        //TODO: fix later
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectField(v.getKey().getName(), v.getValue());
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeArrayFieldStart("expression");
        for (Map.Entry<Value, ObservableList<Expression>> e : userSetting.getExpression().entrySet()) {       //TODO: fix later
            jsonGenerator.writeObjectFieldStart(e.getKey().getName());

            jsonGenerator.writeArrayFieldStart(e.getKey().getName());
            for (Expression expresion : e.getValue()) {
                jsonGenerator.writeObject(expresion);
            }
            jsonGenerator.writeEndArray();

            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }
}
