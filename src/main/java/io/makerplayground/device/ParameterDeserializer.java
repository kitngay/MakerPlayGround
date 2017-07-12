/*
 * Copyright 2017 The Maker Playground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.makerplayground.device;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.makerplayground.helper.ControlType;
import io.makerplayground.helper.DataType;

import java.io.IOException;

/**
 * A helper class used by jackson's {@link ObjectMapper} to deserialize a {@link Parameter}
 * from a json file
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
        DataType dataType = mapper.treeToValue(node.get("dataType"), DataType.class);
        ControlType controlType = mapper.treeToValue(node.get("controlType"), ControlType.class);

        Object defaultValue = null;
        switch (dataType) {
            case STRING:
                defaultValue = mapper.treeToValue(node.get("defaultValue"), String.class);
                break;
            case DOUBLE:
                defaultValue = mapper.treeToValue(node.get("defaultValue"), Double.class);
                break;
            case ENUM:
                defaultValue = mapper.treeToValue(node.get("defaultValue"), String.class);
                break;
            case MIX:
                defaultValue = mapper.treeToValue(node.get("defaultValue"), String.class);
                break;
            case INTEGER:
                defaultValue = mapper.treeToValue(node.get("defaultValue"), Integer.class);
                break;
            default:
                System.out.println("Format error!!!");
        }

        return new Parameter(name, defaultValue, constraint, dataType, controlType);
    }
}