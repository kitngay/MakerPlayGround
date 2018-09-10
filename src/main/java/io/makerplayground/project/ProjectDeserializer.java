package io.makerplayground.project;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.makerplayground.device.*;
import io.makerplayground.helper.*;
import io.makerplayground.project.term.*;
import io.makerplayground.project.expression.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by USER on 14-Jul-17.
 */
public class ProjectDeserializer extends StdDeserializer<Project> {
    public ProjectDeserializer() {
        this(null);
    }

    public ProjectDeserializer(Class<Project> t) {
        super(t);
    }

    @Override
    public Project deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        Project project = new Project();

        String projectName = node.get("projectName").asText();
        project.setProjectName(projectName);

        Platform platform = Platform.valueOf(node.get("controller").get("platform").asText());
        project.setPlatform(platform);

        Device controller = null;
        if (!node.get("controller").get("device").asText().isEmpty()) {
            controller = DeviceLibrary.INSTANCE.getActualDevice().stream().filter(
                    device -> device.getId().equals(node.get("controller").get("device").asText())).findFirst().get();
        }
        project.setController(controller);

        ObservableList<ProjectDevice> inputDevices = FXCollections.observableArrayList();
        for (JsonNode inputDeviceNode : node.get("inputDevice")) {
            ProjectDevice projectDevice = deserializeProjectDevice(mapper, inputDeviceNode, controller);
            inputDevices.add(projectDevice);
            project.addSensor(projectDevice);
        }

        ObservableList<ProjectDevice> outputDevices = FXCollections.observableArrayList();
        for (JsonNode outputDeviceNode : node.get("outputDevice")) {
            ProjectDevice projectDevice = deserializeProjectDevice(mapper, outputDeviceNode, controller);
            outputDevices.add(projectDevice);
            project.addActuator(projectDevice);
        }

        ObservableList<ProjectDevice> connectivityDevices = FXCollections.observableArrayList();
        for (JsonNode connectivityDeviceNode : node.get("connectivityDevice")) {
            ProjectDevice projectDevice = deserializeProjectDevice(mapper, connectivityDeviceNode, controller);
            connectivityDevices.add(projectDevice);
            project.addVirtual(projectDevice);
        }

        Begin begin = project.getBegin();
        begin.setLeft(node.get("begin").get("left").asDouble());
        begin.setTop(node.get("begin").get("top").asDouble());

        for (JsonNode sceneNode : node.get("scene")) {
            Scene scene = deserializeScene(mapper, sceneNode, inputDevices,  outputDevices, connectivityDevices, project);
            project.addScene(scene);
        }

        for(JsonNode conditionNode : node.get("condition")) {
            Condition condition = deserializeCondition(mapper, conditionNode, inputDevices, outputDevices, connectivityDevices, project);
            project.addCondition(condition);
        }

        for (JsonNode lineNode : node.get("line")) {
            NodeElement source = null;
            if (lineNode.get("source").asText().equals("begin")) {  // TODO: hard code
                source = begin;
            } else {
                Optional<Scene> s = project.getScene(lineNode.get("source").asText());
                if (s.isPresent()) {
                    source = s.get();
                }
                Optional<Condition> c = project.getCondition(lineNode.get("source").asText());
                if (c.isPresent()) {
                    source = c.get();
                }
            }

            NodeElement dest = null;
            Optional<Scene> s = project.getScene(lineNode.get("destination").asText());
            if (s.isPresent()) {
                dest = s.get();
            }
            Optional<Condition> c = project.getCondition(lineNode.get("destination").asText());
            if (c.isPresent()) {
                dest = c.get();
            }
            project.addLine(source, dest);
        }

        return project;
    }

    public Scene deserializeScene(ObjectMapper mapper, JsonNode node, ObservableList<ProjectDevice> inputDevice
            , ObservableList<ProjectDevice> outputDevice, ObservableList<ProjectDevice> connectivityDevices, Project project) throws IOException {
        String name = node.get("name").asText();

        List<UserSetting> setting = new ArrayList<>();
        for (JsonNode sceneSettingNode : node.get("setting")) {
            ProjectDevice projectDevice = Stream.concat(outputDevice.stream(), connectivityDevices.stream()).filter(projectDevice1 ->
                    projectDevice1.getName().equals(sceneSettingNode.get("device").asText())).findFirst().get();
            setting.add(deserializeUserSetting(mapper, sceneSettingNode, projectDevice, inputDevice, outputDevice, connectivityDevices));
        }

        double delay = node.get("delay").asDouble();
        Scene.DelayUnit delayUnit = Scene.DelayUnit.valueOf(node.get("delayUnit").asText());

        double top = node.get("position").get("top").asDouble();
        double left = node.get("position").get("left").asDouble();
        double width = node.get("position").get("width").asDouble();
        double height = node.get("position").get("height").asDouble();

        return new Scene(top, left, width, height, name, setting, delay, delayUnit, project);
    }

    public Condition deserializeCondition(ObjectMapper mapper, JsonNode node, ObservableList<ProjectDevice> inputDevice
            , ObservableList<ProjectDevice> outputDevice, ObservableList<ProjectDevice> connectivityDevices, Project project) throws IOException {
        String name = node.get("name").asText();

        List<UserSetting> setting = new ArrayList<>();
        for (JsonNode conditionSettingNode : node.get("setting")) {
            ProjectDevice projectDevice = Stream.concat(inputDevice.stream(), connectivityDevices.stream()).filter(projectDevice1 ->
                    projectDevice1.getName().equals(conditionSettingNode.get("device").asText())).findFirst().get();
            setting.add(deserializeUserSetting(mapper, conditionSettingNode, projectDevice, inputDevice, outputDevice, connectivityDevices));
        }

        double top = node.get("position").get("top").asDouble();
        double left = node.get("position").get("left").asDouble();
        double width = node.get("position").get("width").asDouble();
        double height = node.get("position").get("height").asDouble();

        return new Condition(top, left, width, height, name, setting, project);
    }

    public UserSetting deserializeUserSetting(ObjectMapper mapper, JsonNode node, ProjectDevice projectDevice
            , ObservableList<ProjectDevice> inputDevice, ObservableList<ProjectDevice> outputDevice
            , ObservableList<ProjectDevice> connectivityDevices) throws IOException {
        Set<ProjectDevice> allProjectDevices = new HashSet<>();
        allProjectDevices.addAll(inputDevice);
        allProjectDevices.addAll(outputDevice);
        allProjectDevices.addAll(connectivityDevices);

        Action action = projectDevice.getGenericDevice().getAction(node.get("action").asText());
        // TODO: find a better way
        if (action == null) {
            action = projectDevice.getGenericDevice().getCondition(node.get("action").asText());
        }

        Map<Parameter, Expression> valueMap = new HashMap<>();
        for (JsonNode parameterNode : node.get("valueMap")) {
            Parameter parameter = action.getParameter(parameterNode.get("name").asText());
            Expression expression = null;
            String expressionType = parameterNode.get("type").asText();

            JsonNode valueNode = parameterNode.get("value");
            List<Term> terms = new ArrayList<>();
            for (JsonNode term_node : valueNode.get("terms")) {
                terms.add(deserializeTerm(term_node, allProjectDevices, parameter));
            }
            if (ProjectValueExpression.class.getSimpleName().equals(expressionType)) {
                expression = new ProjectValueExpression(((ValueTerm) terms.get(0)).getValue());
            } else if (CustomNumberExpression.class.getSimpleName().equals(expressionType)) {
                double maxValue = parameterNode.get("maxValue").asDouble();
                double minValue = parameterNode.get("minValue").asDouble();
                expression = new CustomNumberExpression(minValue, maxValue, terms);
            } else if (NumberWithUnitExpression.class.getSimpleName().equals(expressionType)) {
                expression = new NumberWithUnitExpression(((NumberWithUnitTerm) terms.get(0)).getValue());
            } else if (SimpleStringExpression.class.getSimpleName().equals(expressionType)) {
                expression = new SimpleStringExpression(((StringTerm) terms.get(0)).getValue());
            } else if (ValueLinkingExpression.class.getSimpleName().equals(expressionType)){
                expression = new ValueLinkingExpression(parameter, terms);
            } else {
                throw new IllegalStateException("expression type not supported");
            }

            Expression.RefreshInterval refreshInterval = Expression.RefreshInterval.valueOf(valueNode.get("refreshInterval").asText());
            NumberWithUnit interval = mapper.readValue(valueNode.get("userDefinedInterval").traverse()
                    , new TypeReference<NumberWithUnit>(){});
            expression.setRefreshInterval(refreshInterval);
            expression.setUserDefinedInterval(interval);

            valueMap.put(parameter, expression);
        }

        Map<Value, Expression> expressionMap = new HashMap<>();
        Map<Value, Boolean> expressionEnableMap = new HashMap<>();
        for (JsonNode valueNode : node.get("expression")) {
            Value value = projectDevice.getGenericDevice().getValue(valueNode.get("name").asText());
            boolean enable = valueNode.get("enable").asBoolean();
            String type = valueNode.get("type").asText();

            Expression expression;
            if (NumberInRangeExpression.class.getName().contains(type)) {
                expression = deserializeNumberInRangeExpression(mapper, valueNode.get("expression"), projectDevice, value);
            } else {
                throw new IllegalStateException("Unknown expression type");
            }

            expressionMap.put(value, expression);
            expressionEnableMap.put(value, enable);
        }

        return new UserSetting(projectDevice, action, valueMap, expressionMap, expressionEnableMap);
    }

    private Term deserializeTerm(JsonNode term_node,
                                 Collection<ProjectDevice> allProjectDevices,
                                 Parameter parameter) {
        String term_type = term_node.get("type").asText();
        Term term;
        if (Term.Type.NUMBER.name().equals(term_type)) {
            double num = term_node.get("value").get("value").asDouble();
            Unit unit = ((NumericConstraint) parameter.getConstraint()).getUnit();
            NumberWithUnit numberWithUnit = new NumberWithUnit(num, unit);
            term = new NumberWithUnitTerm(numberWithUnit);
        } else if (Term.Type.OPERATOR.name().equals(term_type)) {
            String operator = term_node.get("value").asText();
            term = new OperatorTerm(Operator.valueOf(operator));
        } else if (Term.Type.VALUE.name().equals(term_type)) {
            if ("null".equals(term_node.get("value").asText())) {
                term = new ValueTerm(null);
            } else {
                String projectDeviceName = term_node.get("value").get("name").asText();
                String valueName = term_node.get("value").get("value").asText();
                ProjectDevice device = allProjectDevices.stream().filter(pj -> pj.getName().equals(projectDeviceName)).findFirst().get();
                Value value = device.getGenericDevice().getValue(valueName);
                term = new ValueTerm(new ProjectValue(device, value));
            }
        } else if (Term.Type.STRING.name().equals(term_type)) {
            String word = term_node.get("value").asText();
            term = new StringTerm(word);
        } else {
            throw new IllegalStateException("deserialize unsupported term");
        }
        return term;
    }

    public Expression deserializeNumberInRangeExpression(ObjectMapper mapper, JsonNode node, ProjectDevice device, Value value) {
        if (node.get(0).get("type").asText().equals(Term.Type.VALUE.name())
            && node.get(1).get("type").asText().equals(Term.Type.OPERATOR.name())
                && (node.get(1).get("value").asText().equals(Operator.LESS_THAN.name())
                || node.get(1).get("value").asText().equals(Operator.LESS_THAN_OR_EQUAL.name()))
                && node.get(2).get("type").asText().equals(Term.Type.NUMBER.name())
                && node.get(3).get("type").asText().equals(Term.Type.OPERATOR.name())
                && node.get(3).get("value").asText().equals(Operator.AND.name())
                && node.get(4).get("type").asText().equals(Term.Type.VALUE.name())
                && node.get(5).get("type").asText().equals(Term.Type.OPERATOR.name())
                && (node.get(5).get("value").asText().equals(Operator.GREATER_THAN.name())
                || node.get(5).get("value").asText().equals(Operator.GREATER_THAN_OR_EQUAL.name()))
                && node.get(6).get("type").asText().equals(Term.Type.NUMBER.name())) {
            return new NumberInRangeExpression(device, value)
                    .setLowValue(node.get(6).get("value").get("value").asDouble())
                    .setHighValue(node.get(2).get("value").get("value").asDouble())
                    .setLowOperator(Operator.valueOf(node.get(5).get("value").asText()))
                    .setHighOperator(Operator.valueOf(node.get(1).get("value").asText()));
        } else {
            throw new IllegalStateException("Simple expression parsing fail");
        }
    }

//    public Expression deserializeExpression(ObjectMapper mapper, JsonNode node, ObservableList<ProjectDevice> inputDevice
//            , ObservableList<ProjectDevice> outputDevice, ObservableList<ProjectDevice> connectivityDevices) throws IOException {
//        Expression expression = new Expression();
//
//        for (JsonNode termNode : node) {
//            Term term = null;
//
//            OP type = OP.valueOf(termNode.get("type").asText());
//            if (type == OP.NUMBER) {
//                term = new NumberWithUnitTerm(new NumberWithUnit(termNode.get("value").get("value").asDouble()
//                        , Unit.valueOf(termNode.get("value").get("unit").asText())));
//            } else if (type == OP.STRING) {
//                term = new StringTerm(termNode.get("value").asText());
//            } else if (type == OP.OPERATOR) {
//                term = new OperatorTerm(OP.valueOf(termNode.get("value").asText()));
//            } else if (type == OP.VALUE) {
//                ProjectValue pv = deserializeProjectValue(mapper, termNode.get("value"), inputDevice, outputDevice, connectivityDevices);
//                term = new ValueTerm(pv);
//            } else {
//                throw new IllegalStateException("Found unknown term " + type);
//            }
//
//            expression.getTerms().add(term);
//        }
//
//        return expression;
//    }
//
//    public ProjectValue deserializeProjectValue(ObjectMapper mapper, JsonNode node, ObservableList<ProjectDevice> inputDevice
//            , ObservableList<ProjectDevice> outputDevice, ObservableList<ProjectDevice> connectivityDevices) throws IOException, JsonProcessingException {
//        String deviceName = node.get("name").asText();
//        ProjectDevice device = null;
//        for (ProjectDevice projectDevice : inputDevice) {
//            if (deviceName.equals(projectDevice.getName()))
//                device = projectDevice;
//        }
//        for (ProjectDevice projectDevice : outputDevice) {
//            if (deviceName.equals(projectDevice.getName()))
//                device = projectDevice;
//        }
//        for (ProjectDevice projectDevice : connectivityDevices) {
//            if (deviceName.equals(projectDevice.getName()))
//                device = projectDevice;
//        }
//
//        Value value = device.getGenericDevice().getValue(node.get("value").asText());
//
//        return new ProjectValue(device, value);
//    }

    public ProjectDevice deserializeProjectDevice(ObjectMapper mapper, JsonNode node, Device controller) {
        String name = node.get("name").asText();
        GenericDevice genericDevice = DeviceLibrary.INSTANCE.getGenericDevice(node.get("genericDevice").asText());
        boolean autoSelect = node.get("autoselect").asBoolean();

        String actualDeviceId = node.get("actualDevice").asText();
        Device actualDevice = null;
        if (!actualDeviceId.isEmpty()) {
            actualDevice = DeviceLibrary.INSTANCE.getActualDevice(actualDeviceId);
        }

        Map<Peripheral, List<DevicePort>> actualDeviceConnection = new HashMap<>();
        for  (JsonNode connection : node.get("actualDeviceConnection")) {
            Peripheral source = Peripheral.valueOf(connection.get("devicePeripheral").asText());
            //Peripheral dest = Peripheral.valueOf(connection.get("controllerPeripheral").asText());
            List<DevicePort> port = new ArrayList<>();
            for (JsonNode controllerPeripheral : connection.get("controllerPeripheral")) {
                String portName = controllerPeripheral.asText();
                port.add(controller.getPort(portName));
            }
            actualDeviceConnection.put(source, port);
        }

        String dependentDeviceId = node.get("actualDevice").asText();
        Device dependentDevice = null;
        if (!dependentDeviceId.isEmpty()) {
            dependentDevice = DeviceLibrary.INSTANCE.getActualDevice(dependentDeviceId);
        }

        Map<Peripheral, List<DevicePort>> dependentDeviceConnection = new HashMap<>();
        for (JsonNode connection : node.get("dependentDeviceConnection")) {
            Peripheral source = Peripheral.valueOf(connection.get("devicePeripheral").asText());
            //Peripheral dest = Peripheral.valueOf(connection.get("controllerPeripheral").asText());
            List<DevicePort> port = new ArrayList<>();
            for (JsonNode controllerPeripheral : connection.get("controllerPeripheral")) {
                String portName = controllerPeripheral.asText();
                port.add(controller.getPort(portName));
            }
            actualDeviceConnection.put(source, port);
            dependentDeviceConnection.put(source, port);
        }

        Map<Property, String> property = new HashMap<>();
        for (JsonNode propertyNode : node.get("property")) {
            String propertyName = propertyNode.get("name").asText();
            Property p = genericDevice.getProperty(propertyName);
            property.put(p, propertyNode.get("value").asText());
        }

        return new ProjectDevice(name, genericDevice, autoSelect, actualDevice, actualDeviceConnection
                , dependentDevice, dependentDeviceConnection, property);
    }
}
