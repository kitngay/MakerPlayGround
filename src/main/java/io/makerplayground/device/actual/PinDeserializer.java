/*
 * Copyright (c) 2019. The Maker Playground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.makerplayground.device.actual;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.makerplayground.util.DeserializerHelper.createArrayNodeIfMissing;
import static io.makerplayground.util.DeserializerHelper.throwIfMissingField;

public class PinDeserializer extends JsonDeserializer<Pin> {
    @Override
    public Pin deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        JsonNode node = mapper.readTree(jsonParser);

        throwIfMissingField(node, "name", "pin");

        String displayName = node.get("name").asText();
        List<String> codingName = node.has("coding_name") ? mapper.readValue(node.get("coding_name").traverse(), new TypeReference<List<String>>() {}) : Collections.emptyList();

        throwIfMissingField(node, "x", "pin", displayName, "x");
        double x = node.get("x").asDouble();

        throwIfMissingField(node, "y", "pin", displayName, "y");
        double y = node.get("y").asDouble();

        VoltageLevel voltageLevel = node.has("voltage_level") ? VoltageLevel.valueOf(node.get("voltage_level").asText()) : null;

        createArrayNodeIfMissing(node, "function");
        List<PinFunction> functions;
        if (node.get("function").isArray()) {
            functions = mapper.readValue(node.get("function").traverse(), new TypeReference<List<PinFunction>>() {});
        } else {
            functions = List.of(PinFunction.valueOf(node.get("function").asText()));
        }

        if (node.has("connect_to")) {
            String connectTo = node.get("connect_to").asText();
            return new IntegratedPin(displayName, codingName, voltageLevel, functions, x, y, null, connectTo);
        } else {
            return new Pin(displayName, codingName, voltageLevel, functions, x, y, null);
        }
    }
}
