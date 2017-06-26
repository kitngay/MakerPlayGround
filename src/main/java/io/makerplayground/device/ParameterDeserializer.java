package io.makerplayground.device;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Created by nuntipat on 6/25/2017 AD.
 */
public class ParameterDeserializer extends StdDeserializer<Parameter> {

    public ParameterDeserializer() {
        this(null);
    }

    public ParameterDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Parameter deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        String name = node.get("name").asText();
        Constraint constraint = mapper.treeToValue(node.get("constraint"), Constraint.class);
        ParameterType parameterType = mapper.treeToValue(node.get("type"), ParameterType.class);
        ControlType controlType = mapper.treeToValue(node.get("control"), ControlType.class);

        Object defaultValue = null;
        switch (parameterType) {
            case STRING:
                defaultValue = mapper.treeToValue(node.get("default"), String.class);
                break;
            case DOUBLE:
                defaultValue = mapper.treeToValue(node.get("default"), Double.class);
                break;
            default:
                System.out.println("Format error!!!");
        }

        return new Parameter(name, defaultValue, constraint, parameterType, controlType);
    }
}
