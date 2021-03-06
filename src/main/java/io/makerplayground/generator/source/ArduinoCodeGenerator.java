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

package io.makerplayground.generator.source;

import io.makerplayground.device.actual.*;
import io.makerplayground.device.shared.*;
import io.makerplayground.device.shared.constraint.NumericConstraint;
import io.makerplayground.generator.devicemapping.ProjectLogic;
import io.makerplayground.generator.devicemapping.ProjectMappingResult;
import io.makerplayground.project.*;
import io.makerplayground.project.Condition;
import io.makerplayground.project.expression.*;
import io.makerplayground.project.term.*;
import io.makerplayground.util.AzureCognitiveServices;
import io.makerplayground.util.AzureIoTHubDevice;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ArduinoCodeGenerator {

    static final String INDENT = "    ";
    static final String NEW_LINE = "\n";

    final Project project;
    final ProjectConfiguration configuration;
    final StringBuilder builder = new StringBuilder();
    private final List<Scene> allSceneUsed;
    private final List<Condition> allConditionUsed;
    private final List<List<ProjectDevice>> projectDeviceGroup;
    private final List<Delay> allDelayUsed;

    protected static final Set<PinFunction> PIN_FUNCTION_WITH_CODES = Set.of(
            PinFunction.DIGITAL_IN, PinFunction.DIGITAL_OUT,
            PinFunction.ANALOG_IN, PinFunction.ANALOG_OUT,
            PinFunction.PWM_OUT,
            PinFunction.INTERRUPT_LOW, PinFunction.INTERRUPT_HIGH, PinFunction.INTERRUPT_CHANGE, PinFunction.INTERRUPT_RISING, PinFunction.INTERRUPT_FALLING,
            PinFunction.HW_SERIAL_RX, PinFunction.HW_SERIAL_TX, PinFunction.SW_SERIAL_RX, PinFunction.SW_SERIAL_TX
    );

    private ArduinoCodeGenerator(Project project) {
        this.project = project;
        this.configuration = project.getProjectConfiguration();
        Set<NodeElement> allNodeUsed = Utility.getAllUsedNodes(project);
        this.allSceneUsed = Utility.takeScene(allNodeUsed);
        this.allConditionUsed = Utility.takeCondition(allNodeUsed);
        this.allDelayUsed = Utility.takeDelay(allNodeUsed);
        this.projectDeviceGroup = project.getAllDeviceUsedGroupBySameActualDevice();
    }

    static SourceCodeResult generateCode(Project project) {
        ArduinoCodeGenerator generator = new ArduinoCodeGenerator(project);
        // Check if the diagram (only the connected nodes) are all valid.
        if (!Utility.validateDiagram(project)) {
            return new SourceCodeResult(SourceCodeError.DIAGRAM_ERROR, "-");
        }
        // Check if all used devices are assigned.
        if (ProjectLogic.validateDeviceAssignment(project) != ProjectMappingResult.OK) {
            return new SourceCodeResult(SourceCodeError.NOT_SELECT_DEVICE_OR_PORT, "-");
        }
        if (!Utility.validateDeviceProperty(project)) {
            return new SourceCodeResult(SourceCodeError.MISSING_PROPERTY, "-");   // TODO: add location
        }
        if (project.getCloudPlatformUsed().size() > 1) {
            return new SourceCodeResult(SourceCodeError.MORE_THAN_ONE_CLOUD_PLATFORM, "-");
        }
        generator.appendHeader(project.getAllDeviceUsed(), project.getCloudPlatformUsed());
        generator.appendBeginRecentSceneFinishTime();
        generator.appendPointerVariables();
//        generator.appendProjectValue();
        generator.appendFunctionDeclaration();
//        generator.appendTaskVariables();
        generator.appendInstanceVariables(project.getCloudPlatformUsed());
        generator.appendSetupFunction();
        generator.appendLoopFunction();
        generator.appendUpdateFunction();
        for (Begin begin : generator.project.getBegin()) {
            generator.appendBeginFunction(begin);
        }
        generator.appendSceneFunctions();
        generator.appendConditionFunctions();
        return new SourceCodeResult(generator.builder.toString());
    }

    private void appendPointerVariables() {
        project.getBegin().forEach(begin -> builder.append("void (*").append(ArduinoCodeUtility.parsePointerName(begin)).append(")(void);").append(NEW_LINE));
    }

    private void appendHeader(Collection<ProjectDevice> devices, Collection<CloudPlatform> cloudPlatforms) {
        builder.append("#include \"MakerPlayground.h\"").append(NEW_LINE);

        // generate include
        Stream<String> device_libs = devices.stream()
                .filter(projectDevice -> configuration.getActualDeviceOrActualDeviceOfIdenticalDevice(projectDevice).isPresent())
                .map(projectDevice -> configuration.getActualDeviceOrActualDeviceOfIdenticalDevice(projectDevice).orElseThrow().getMpLibrary(project.getSelectedPlatform()));
        Stream<String> cloud_libs = cloudPlatforms.stream()
                .flatMap(cloudPlatform -> Stream.of(cloudPlatform.getLibName(), project.getSelectedController().getCloudPlatformLibraryName(cloudPlatform)));
        Stream.concat(device_libs, cloud_libs).distinct().sorted().forEach(s -> builder.append(ArduinoCodeUtility.parseIncludeStatement(s)).append(NEW_LINE));
        builder.append(NEW_LINE);
    }

    private void appendFunctionDeclaration() {
        for (Begin begin : project.getBegin()) {
            // generate function declaration for task node scene
            builder.append("void ").append(ArduinoCodeUtility.parseNodeFunctionName(begin)).append("();").append(NEW_LINE);

            // generate function declaration for first level condition(s) or delay(s) connected to the task node block
            List<Condition> conditions = Utility.findAdjacentConditions(project, begin);
            List<Delay> delays = Utility.findAdjacentDelays(project, begin);
            if (!conditions.isEmpty() || !delays.isEmpty()) {
                builder.append("void ").append(ArduinoCodeUtility.parseConditionFunctionName(begin)).append("();").append(NEW_LINE);
            }
        }

        // generate function declaration for each scene and their conditions/delays
        for (Scene scene : allSceneUsed) {
            builder.append("void ").append(ArduinoCodeUtility.parseNodeFunctionName(scene)).append("();").append(NEW_LINE);
            List<Condition> adjacentCondition = Utility.findAdjacentConditions(project, scene);
            List<Delay> delays = Utility.findAdjacentDelays(project, scene);
            if (!adjacentCondition.isEmpty() || !delays.isEmpty()) {
                builder.append("void ").append(ArduinoCodeUtility.parseConditionFunctionName(scene)).append("();").append(NEW_LINE);
            }
        }

        // generate function declaration for each condition that has conditions/delays
        for (Condition condition : allConditionUsed) {
            List<Condition> adjacentCondition = Utility.findAdjacentConditions(project, condition);
            List<Delay> delays = Utility.findAdjacentDelays(project, condition);
            if (!adjacentCondition.isEmpty() || !delays.isEmpty()) {
                builder.append("void ").append(ArduinoCodeUtility.parseConditionFunctionName(condition)).append("();").append(NEW_LINE);
            }
        }

        // generate function declaration for each delay that has conditions/delays
        for (Delay delay : allDelayUsed) {
            List<Condition> adjacentCondition = Utility.findAdjacentConditions(project, delay);
            List<Delay> delays = Utility.findAdjacentDelays(project, delay);
            if (!adjacentCondition.isEmpty() || !delays.isEmpty()) {
                builder.append("void ").append(ArduinoCodeUtility.parseConditionFunctionName(delay)).append("();").append(NEW_LINE);
            }
        }

        builder.append(NEW_LINE);
    }

    private void appendInstanceVariables(Collection<CloudPlatform> cloudPlatforms) {
        // create cloud singleton variables
        for (CloudPlatform cloudPlatform: cloudPlatforms) {
            String cloudPlatformLibName = cloudPlatform.getLibName();
            String specificCloudPlatformLibName = project.getSelectedController().getCloudPlatformSourceCodeLibrary().get(cloudPlatform).getClassName();

            List<String> cloudPlatformParameterValues = cloudPlatform.getParameter().stream()
                    .map(param -> "\"" + project.getCloudPlatformParameter(cloudPlatform, param) + "\"").collect(Collectors.toList());
            builder.append(cloudPlatformLibName).append("* ").append(ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform))
                    .append(" = new ").append(specificCloudPlatformLibName)
                    .append("(").append(String.join(", ", cloudPlatformParameterValues)).append(");").append(NEW_LINE);
        }

        for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
            if (projectDeviceList.isEmpty()) {
                throw new IllegalStateException();
            }
            Optional<ActualDevice> actualDeviceOptional = configuration.getActualDeviceOrActualDeviceOfIdenticalDevice(projectDeviceList.get(0));
            if (actualDeviceOptional.isEmpty()) {
                throw new IllegalStateException();
            }
            ActualDevice actualDevice = actualDeviceOptional.get();
            builder.append(actualDevice.getMpLibrary(project.getSelectedPlatform()))
                    .append(" ").append(ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList));
            List<String> args = new ArrayList<>();

            DeviceConnection connection = project.getProjectConfiguration().getDeviceConnection(projectDeviceList.get(0));
            if (connection != DeviceConnection.NOT_CONNECTED) {
                Map<Connection, Connection> connectionMap = connection.getConsumerProviderConnections();
                for (Connection connectionConsume: actualDevice.getConnectionConsumeByOwnerDevice(projectDeviceList.get(0))) {
                    Connection connectionProvide = connectionMap.get(connectionConsume);
                    for (int i=connectionConsume.getPins().size()-1; i>=0; i--) {
                        Pin pinConsume = connectionConsume.getPins().get(i);
                        Pin pinProvide = connectionProvide.getPins().get(i);
                        if (pinConsume.getFunction().get(0) == PinFunction.NO_FUNCTION) {
                            continue;
                        }
                        List<PinFunction> possibleFunctionConsume = pinConsume.getFunction().get(0).getPossibleConsume();
                        for (PinFunction function: possibleFunctionConsume) {
                            if (pinProvide.getFunction().contains(function)) {
                                if (PIN_FUNCTION_WITH_CODES.contains(function)) {
                                    if (!pinProvide.getCodingName().isEmpty()) {
                                        args.add(pinProvide.getCodingName());
                                    } else {
                                        args.add(pinProvide.getRefTo());
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }

            // property for the generic device
            for (Property p : actualDevice.getProperty()) {
                ProjectDevice projectDevice = configuration.getRootDevice(projectDeviceList.get(0));
                Object value = configuration.getPropertyValue(projectDevice, p);
                if (value == null) {
                    throw new IllegalStateException("Property hasn't been set");
                }
                switch (p.getDataType()) {
                    case INTEGER:
                    case DOUBLE:
                        args.add(String.valueOf(((NumberWithUnit) value).getValue()));
                        break;
                    case INTEGER_ENUM:
                    case BOOLEAN_ENUM:
                        args.add(String.valueOf(value));
                        break;
                    case STRING:
                    case ENUM:
                        args.add("\"" + value + "\"");
                        break;
                    case AZURE_COGNITIVE_KEY:
                        AzureCognitiveServices acs = (AzureCognitiveServices) value;
                        args.add("\"" + acs.getLocation().toLowerCase() + "\"");
                        args.add("\"" + acs.getKey1() + "\"");
                        break;
                    case AZURE_IOTHUB_KEY:
                        AzureIoTHubDevice azureIoTHubDevice = (AzureIoTHubDevice) value;
                        args.add("\"" + azureIoTHubDevice.getConnectionString() + "\"");
                        break;
                    default:
                        throw new IllegalStateException("Property (" + value + ") hasn't been supported yet");
                }
            }

            // Cloud Platform instance
            CloudPlatform cloudPlatform = actualDevice.getCloudConsume();
            if (cloudPlatform != null) {
                args.add(ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform));
            }

            if (!args.isEmpty()) {
                builder.append("(").append(String.join(", ", args)).append(");").append(NEW_LINE);
            } else {
                builder.append(";").append(NEW_LINE);
            }
        }
        builder.append(NEW_LINE);
    }

    private void appendBeginRecentSceneFinishTime() {
        project.getBegin().forEach(taskNode -> builder.append("unsigned long ").append(parseBeginRecentSceneFinishTime(taskNode)).append(" = 0;").append(NEW_LINE));
    }

//    private void appendProjectValue() {
//        Map<ProjectDevice, Set<Value>> variableMap = project.getAllValueUsedMap(EnumSet.of(DataType.DOUBLE, DataType.INTEGER));
//        for (ProjectDevice projectDevice : variableMap.keySet()) {
//            for (Value v : variableMap.get(projectDevice)) {
//                builder.append("double ").append(parseValueVariableTerm(configuration, projectDevice, v)).append(";").append(NEW_LINE);
//            }
//        }
//        builder.append(NEW_LINE);
//    }

    private void appendSetupFunction() {
        // generate setup function
        builder.append("void setup() {").append(NEW_LINE);
        builder.append(INDENT).append("Serial.begin(115200);").append(NEW_LINE);

        if (project.getSelectedPlatform().equals(Platform.ARDUINO_ESP32)) {
            builder.append(INDENT).append("analogSetWidth(10);").append(NEW_LINE);
        }

        if (!project.getProjectConfiguration().useHwSerialProperty().get()) {
            for (CloudPlatform cloudPlatform : project.getCloudPlatformUsed()) {
                String cloudPlatformVariableName = ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform);
                builder.append(INDENT).append("status_code = ").append(cloudPlatformVariableName).append("->init();").append(NEW_LINE);
                builder.append(INDENT).append("if (status_code != 0) {").append(NEW_LINE);
                builder.append(INDENT).append(INDENT).append("MP_ERR(\"").append(cloudPlatform.getDisplayName()).append("\", status_code);").append(NEW_LINE);
                builder.append(INDENT).append(INDENT).append("while(1);").append(NEW_LINE);
                builder.append(INDENT).append("}").append(NEW_LINE);
                builder.append(NEW_LINE);
            }

            for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
                String variableName = ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList);
                builder.append(INDENT).append("status_code = ").append(variableName).append(".init();").append(NEW_LINE);
                builder.append(INDENT).append("if (status_code != 0) {").append(NEW_LINE);
                builder.append(INDENT).append(INDENT).append("MP_ERR(\"").append(projectDeviceList.stream().map(ProjectDevice::getName).collect(Collectors.joining(", "))).append("\", status_code);").append(NEW_LINE);
                builder.append(INDENT).append(INDENT).append("while(1);").append(NEW_LINE);
                builder.append(INDENT).append("}").append(NEW_LINE);
                builder.append(NEW_LINE);
            }
        }
        project.getBegin().forEach(begin -> builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(begin)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(begin)).append(";").append(NEW_LINE));
        builder.append("}").append(NEW_LINE);
        builder.append(NEW_LINE);
    }

    private void appendLoopFunction() {
        builder.append("void loop() {").append(NEW_LINE);
        builder.append(INDENT).append("update();").append(NEW_LINE);
        project.getBegin().forEach(begin ->
            builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(begin)).append("();").append(NEW_LINE)
        );
        builder.append("}").append(NEW_LINE);
        builder.append(NEW_LINE);
    }

//    private void appendTaskVariables() {
//        Set<ProjectDevice> devices = Utility.getUsedDevicesWithTask(project);
//        if (!devices.isEmpty()) {
//            for (ProjectDevice projectDevice : devices) {
//                builder.append("Task ").append(parseDeviceTaskVariableName(configuration, projectDevice)).append(" = NULL;").append(NEW_LINE);
//                builder.append("Expr ").append(parseDeviceExpressionVariableName(configuration, projectDevice))
//                        .append("[").append(Utility.getMaximumNumberOfExpression(project, projectDevice)).append("];").append(NEW_LINE);
//            }
//            builder.append(NEW_LINE);
//        }
//    }

    private void appendUpdateFunction() {
        builder.append("void update() {").append(NEW_LINE);
        builder.append(INDENT).append("currentTime = millis();").append(NEW_LINE);
        builder.append(NEW_LINE);

        // allow all cloud platform maintains their own tasks (e.g. connection)
        for (CloudPlatform cloudPlatform : project.getCloudPlatformUsed()) {
            builder.append(INDENT).append(ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform)).append("->update(currentTime);").append(NEW_LINE);
        }

        // allow all devices to perform their own tasks
        for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
            builder.append(INDENT).append(ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList)).append(".update(currentTime);").append(NEW_LINE);
        }
        builder.append(NEW_LINE);

//        // retrieve all project values
        Map<ProjectDevice, Set<Value>> valueUsed = project.getAllValueUsedMap(EnumSet.of(DataType.DOUBLE, DataType.INTEGER));
//        for (ProjectDevice projectDevice : valueUsed.keySet()) {
//            for (Value v : valueUsed.get(projectDevice)) {
//                builder.append(INDENT).append(parseValueVariableTerm(configuration, projectDevice, v)).append(" = ")
//                        .append(parseDeviceVariableName(configuration, projectDevice)).append(".get")
//                        .append(v.getName().replace(" ", "_").replace(".", "_")).append("();").append(NEW_LINE);
//            }
//        }
//        if (!valueUsed.isEmpty()) {
//            builder.append(NEW_LINE);
//        }

//        // recompute expression's value
//        for (ProjectDevice projectDevice : Utility.getUsedDevicesWithTask(project)) {
//            builder.append(INDENT).append("evaluateExpression(").append(parseDeviceTaskVariableName(configuration, projectDevice)).append(", ")
//                    .append(parseDeviceExpressionVariableName(configuration, projectDevice)).append(", ")
//                    .append(Utility.getMaximumNumberOfExpression(project, projectDevice)).append(");").append(NEW_LINE);
//        }
//        if (!Utility.getUsedDevicesWithTask(project).isEmpty()) {
//            builder.append(NEW_LINE);
//        }

        // log status of each devices
        if (!project.getProjectConfiguration().useHwSerialProperty().get()) {
            builder.append(INDENT).append("if (currentTime - latestLogTime > MP_LOG_INTERVAL) {").append(NEW_LINE);
//            for (CloudPlatform cloudPlatform : project.getCloudPlatformUsed()) {
//                builder.append(INDENT).append(INDENT).append("MP_LOG_P(").append(ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform))
//                        .append(", \"").append(cloudPlatform.getDisplayName()).append("\");").append(NEW_LINE);
//            }
//
//            for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
//                builder.append(INDENT).append(INDENT).append("MP_LOG(").append(ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList))
//                        .append(", \"").append(projectDeviceList.stream().map(ProjectDevice::getName).collect(Collectors.joining(", ")))
//                        .append("\");").append(NEW_LINE);
//            }

            for (ProjectDevice projectDevice : valueUsed.keySet()) {
                if (!valueUsed.get(projectDevice).isEmpty()) {
                    builder.append(INDENT).append(INDENT).append("PR_VAL(); PR_DEVICE(F(\"").append(projectDevice.getName()).append("\"));").append(NEW_LINE);
                    builder.append(INDENT).append(INDENT).append(valueUsed.get(projectDevice).stream()
                            .map(value -> "Serial.print(\"" + value.getName() + "=\"); Serial.print(" + ArduinoCodeUtility.parseValueVariableTerm(searchGroup(projectDevice), value) + ");")
                            .collect(Collectors.joining(" Serial.print(\",\");" + NEW_LINE + INDENT + INDENT)));
                    builder.append("PR_END();").append(NEW_LINE);
                }
            }

            builder.append(INDENT).append(INDENT).append("latestLogTime = millis();").append(NEW_LINE);
            builder.append(INDENT).append("}").append(NEW_LINE);
        }

        if (project.getSelectedPlatform() == Platform.ARDUINO_ESP8266) {
            builder.append(INDENT).append("yield();").append(NEW_LINE);
        }
        builder.append("}").append(NEW_LINE);
    }

    private void appendBeginFunction(NodeElement nodeElement) {
        List<NodeElement> adjacentVertices = Utility.findAdjacentNodes(project, nodeElement);
        List<Scene> adjacentScene = Utility.takeScene(adjacentVertices);
        List<Condition> adjacentCondition = Utility.takeCondition(adjacentVertices);
        List<Delay> adjacentDelay = Utility.takeDelay(adjacentVertices);

        // generate code for begin
        builder.append(NEW_LINE);
        builder.append("void ").append(ArduinoCodeUtility.parseNodeFunctionName(nodeElement)).append("() {").append(NEW_LINE);
        if (!adjacentScene.isEmpty()) { // if there is any adjacent scene, move to that scene and ignore condition (short circuit)
            if (adjacentScene.size() != 1) {
                throw new IllegalStateException("Connection to multiple scene from the same source is not allowed");
            }
            Scene currentScene = adjacentScene.get(0);
            builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(nodeElement)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(currentScene)).append(";").append(NEW_LINE);
        } else if (!adjacentCondition.isEmpty() || !adjacentDelay.isEmpty()) { // there is a condition so we generate code for that condition
            builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(nodeElement)).append(" = ").append(ArduinoCodeUtility.parseConditionFunctionName(nodeElement)).append(";").append(NEW_LINE);
        }
        // do nothing if there isn't any scene or condition
        builder.append("}").append(NEW_LINE);
    }

    private void appendSceneFunctions() {
        Set<NodeElement> visitedNodes = new HashSet<>();
        List<NodeElement> adjacentNodes;
        List<Scene> adjacentScene;
        List<Condition> adjacentCondition;
        List<Delay> adjacentDelay;
        Queue<NodeElement> nodeToTraverse = new ArrayDeque<>(project.getBegin());
        while (!nodeToTraverse.isEmpty()) {
            // Remove node from queue
            NodeElement node = nodeToTraverse.remove();
            // There can be the node that already visited after we add to traversing list.
            if(visitedNodes.contains(node)) {
                continue;
            }
            // Add to visited set
            visitedNodes.add(node);
            // Add next unvisited node to queue
            adjacentNodes = Utility.findAdjacentNodes(project, node);
            adjacentScene = Utility.takeScene(adjacentNodes);
            adjacentCondition = Utility.takeCondition(adjacentNodes);
            adjacentDelay = Utility.takeDelay(adjacentNodes);
            nodeToTraverse.addAll(adjacentScene.stream().filter(scene -> !visitedNodes.contains(scene)).collect(Collectors.toSet()));
            nodeToTraverse.addAll(adjacentCondition.stream().filter(condition -> !visitedNodes.contains(condition)).collect(Collectors.toSet()));
            nodeToTraverse.addAll(adjacentDelay.stream().filter(delay -> !visitedNodes.contains(delay)).collect(Collectors.toSet()));

            // Generate code for node
            if (node instanceof Scene) {
                Scene currentScene = (Scene) node;
                Begin root = node.getRoot();

                // create function header
                builder.append(NEW_LINE);
                builder.append("void ").append(ArduinoCodeUtility.parseNodeFunctionName(currentScene)).append("() {").append(NEW_LINE);
                builder.append(INDENT).append("update();").append(NEW_LINE);
                // do action
                for (UserSetting setting : currentScene.getSetting()) {
                    ProjectDevice device = setting.getDevice();
                    String deviceName = ArduinoCodeUtility.parseDeviceVariableName(searchGroup(device));
                    List<String> taskParameter = new ArrayList<>();

                    List<Parameter> parameters = setting.getAction().getParameter();
                    if (setting.isDataBindingUsed()) {  // generate task based code for performing action continuously in background
//                        int parameterIndex = 0;
//                        for (Parameter p : parameters) {
//                            Expression e = setting.getParameterMap().get(p);
//                            if (setting.isDataBindingUsed(p)) {
//                                String expressionVarName = parseDeviceExpressionVariableName(configuration, device) + "[" + parameterIndex + "]";
//                                parameterIndex++;
//                                builder.append(INDENT).append("setExpression(").append(expressionVarName).append(", ")
//                                        .append("[]()->double{").append("return ").append(parseExpressionForParameter(p, e)).append(";}, ")
//                                        .append(parseRefreshInterval(e)).append(");").append(NEW_LINE);
//                                taskParameter.add(expressionVarName + ".value");
//                            } else {
//                                taskParameter.add(parseExpressionForParameter(p, e));
//                            }
//                        }
//                        for (int i = parameterIndex; i < Utility.getMaximumNumberOfExpression(project, setting.getDevice()); i++) {
//                            builder.append(INDENT).append("clearExpression(").append(parseDeviceExpressionVariableName(configuration, device))
//                                    .append("[").append(i).append("]);").append(NEW_LINE);
//                        }
//                        builder.append(INDENT).append("setTask(").append(parseDeviceTaskVariableName(configuration, device)).append(", []() -> void {")
//                                .append(deviceName).append(".").append(setting.getAction().getFunctionName()).append("(")
//                                .append(String.join(", ", taskParameter)).append(");});").append(NEW_LINE);
                    } else {    // generate code to perform action once
//                        // unsetDevice task if this device used to have background task set
//                        if (Utility.getUsedDevicesWithTask(project).contains(device)) {
//                            builder.append(INDENT).append(parseDeviceTaskVariableName(configuration, device)).append(" = NULL;").append(NEW_LINE);
//                        }
                        // generate code to perform the action
                        for (Parameter p : parameters) {
                            taskParameter.add(parseExpressionForParameter(p, setting.getParameterMap().get(p)));
                        }
                        builder.append(INDENT).append(deviceName).append(".").append(setting.getAction().getFunctionName())
                                .append("(").append(String.join(", ", taskParameter)).append(");").append(NEW_LINE);
                    }
                }

                // used for time elapsed condition and delay
                builder.append(INDENT).append(parseBeginRecentSceneFinishTime(root)).append(" = millis();").append(NEW_LINE);

                if (!adjacentScene.isEmpty()) { // if there is any adjacent scene, move to that scene and ignore condition (short circuit)
                    if (adjacentScene.size() != 1) {
                        throw new IllegalStateException("Connection to multiple scene from the same source is not allowed");
                    }
                    Scene s = adjacentScene.get(0);
                    builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(s)).append(";").append(NEW_LINE);
                } else if (!adjacentCondition.isEmpty() || !adjacentDelay.isEmpty()) {
                    builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseConditionFunctionName(currentScene)).append(";").append(NEW_LINE);
                } else {
                    builder.append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(root)).append(";").append(NEW_LINE);
                }

                // end of scene's function
                builder.append("}").append(NEW_LINE);
            }
        }
    }

    private void appendConditionFunctions() {
        Set<NodeElement> visitedNodes = new HashSet<>();
        List<NodeElement> adjacentNodes;
        List<Scene> adjacentScene;
        List<Condition> adjacentCondition;
        List<Delay> adjacentDelay;
        Queue<NodeElement> nodeToTraverse = new ArrayDeque<>(project.getBegin());
        while (!nodeToTraverse.isEmpty()) {
            // Remove node from queue
            NodeElement node = nodeToTraverse.remove();
            // There can be the node that already visited after we add to traversing list.
            if(visitedNodes.contains(node)) {
                continue;
            }
            // Add to visited set
            visitedNodes.add(node);
            // Add next unvisited node to queue
            adjacentNodes = Utility.findAdjacentNodes(project, node);
            adjacentScene = Utility.takeScene(adjacentNodes);
            adjacentCondition = Utility.takeCondition(adjacentNodes);
            adjacentDelay = Utility.takeDelay(adjacentNodes);
            nodeToTraverse.addAll(adjacentScene.stream().filter(scene -> !visitedNodes.contains(scene)).collect(Collectors.toList()));
            nodeToTraverse.addAll(adjacentCondition.stream().filter(condition -> !visitedNodes.contains(condition)).collect(Collectors.toList()));
            nodeToTraverse.addAll(adjacentDelay.stream().filter(delay -> !visitedNodes.contains(delay)).collect(Collectors.toSet()));

            if (!adjacentCondition.isEmpty() || !adjacentDelay.isEmpty()) {
                Begin root = node.getRoot();

                builder.append(NEW_LINE);
                builder.append("void ").append(ArduinoCodeUtility.parseConditionFunctionName(node)).append("() {").append(NEW_LINE);

                // call the update function
                builder.append(INDENT).append("update();").append(NEW_LINE);
                // generate if for delay
                if (!adjacentDelay.isEmpty()) {
                    if (adjacentDelay.size() != 1) {
                        throw new IllegalStateException("Connecting multiple delay to the same node is not allowed");
                    }
                    Delay currentDelay = adjacentDelay.get(0);
                    double delayInMillisecond = 0.0;
                    if (currentDelay.getDelayUnit() == DelayUnit.SECOND) {
                        delayInMillisecond = currentDelay.getDelayValue() * 1000.0;
                    } else if (currentDelay.getDelayUnit() == DelayUnit.MILLISECOND) {
                        delayInMillisecond = currentDelay.getDelayValue();
                    } else {
                        throw new IllegalStateException();
                    }
                    builder.append(INDENT).append("if (millis() > ").append(parseBeginRecentSceneFinishTime(root)).append(" + ").append(delayInMillisecond).append(") {").append(NEW_LINE);
                    List<NodeElement> nextNodes = Utility.findAdjacentNodes(project, currentDelay);
                    List<Scene> nextScene = Utility.takeScene(nextNodes);
                    List<Condition> nextCondition = Utility.takeCondition(nextNodes);
                    List<Delay> nextDelay = Utility.takeDelay(nextNodes);

                    if (!nextScene.isEmpty()) { // if there is any adjacent scene, move to that scene and ignore condition (short circuit)
                        if (nextScene.size() != 1) {
                            throw new IllegalStateException("Connection to multiple scene from the same source is not allowed");
                        }
                        Scene s = nextScene.get(0);
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(s)).append(";").append(NEW_LINE);
                    } else if (!nextCondition.isEmpty() || !nextDelay.isEmpty()) {
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseConditionFunctionName(currentDelay)).append(";").append(NEW_LINE);
                    } else {
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(root)).append(";").append(NEW_LINE);
                    }

                    builder.append(INDENT).append("}").append(NEW_LINE); // end of if
                }
                // generate if for each condition
                for (Condition condition : adjacentCondition) {
                    List<String> booleanExpressions = new ArrayList<>();
                    for (UserSetting setting : condition.getVirtualDeviceSetting()) {
                        if (setting.getCondition() == null) {
                            throw new IllegalStateException("UserSetting {" + setting + "}'s condition must be set ");
                        } else if (setting.getDevice() == VirtualProjectDevice.timeElapsedProjectDevice) {
                            Parameter valueParameter = setting.getCondition().getParameter().get(0);
                            if (setting.getCondition() == VirtualProjectDevice.lessThan) {
                                booleanExpressions.add("millis() < " + parseBeginRecentSceneFinishTime(root) + " + " +
                                        parseExpressionForParameter(valueParameter, setting.getParameterMap().get(valueParameter)));
                            } else {
                                booleanExpressions.add("millis() > " + parseBeginRecentSceneFinishTime(root) + " + " +
                                        parseExpressionForParameter(valueParameter, setting.getParameterMap().get(valueParameter)));
                            }
                        } else {
                            throw new IllegalStateException("Found unsupported user setting {" + setting + "}");
                        }
                    }
                    for (UserSetting setting : condition.getSetting()) {
                        if (setting.getCondition() == null) {
                            throw new IllegalStateException("UserSetting {" + setting + "}'s condition must be set ");
                        } else if (!setting.getCondition().getName().equals("Compare")) {
                            List<String> params = new ArrayList<>();
                            setting.getCondition().getParameter().forEach(parameter -> params.add(parseExpressionForParameter(parameter, setting.getParameterMap().get(parameter))));
                            booleanExpressions.add(ArduinoCodeUtility.parseDeviceVariableName(searchGroup(setting.getDevice())) + "." +
                                    setting.getCondition().getFunctionName() + "(" + String.join(",", params) + ")");
                        } else {
                            for (Value value : setting.getExpression().keySet()) {
                                if (setting.getExpressionEnable().get(value)) {
                                    Expression expression = setting.getExpression().get(value);
                                    booleanExpressions.add("(" + parseTerms(expression.getTerms()) + ")");
                                }
                            }
                        }
                    }
                    if (booleanExpressions.isEmpty()) {
                        throw new IllegalStateException("Found an empty condition block: " + condition);
                    }
                    builder.append(INDENT).append("if").append("(");
                    builder.append(String.join(" && ", booleanExpressions)).append(") {").append(NEW_LINE);

                    // used for time elapsed condition and delay
                    builder.append(INDENT).append(INDENT).append(parseBeginRecentSceneFinishTime(root)).append(" = millis();").append(NEW_LINE);

                    List<NodeElement> nextNodes = Utility.findAdjacentNodes(project, condition);
                    List<Scene> nextScene = Utility.takeScene(nextNodes);
                    List<Condition> nextCondition = Utility.takeCondition(nextNodes);
                    List<Delay> nextDelay = Utility.takeDelay(nextNodes);

                    if (!nextScene.isEmpty()) { // if there is any adjacent scene, move to that scene and ignore condition (short circuit)
                        if (nextScene.size() != 1) {
                            throw new IllegalStateException("Connection to multiple scene from the same source is not allowed");
                        }
                        Scene s = nextScene.get(0);
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(s)).append(";").append(NEW_LINE);
                    } else if (!nextCondition.isEmpty() || !nextDelay.isEmpty()) {
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseConditionFunctionName(condition)).append(";").append(NEW_LINE);
                    } else {
                        builder.append(INDENT).append(INDENT).append(ArduinoCodeUtility.parsePointerName(root)).append(" = ").append(ArduinoCodeUtility.parseNodeFunctionName(root)).append(";").append(NEW_LINE);
                    }

                    builder.append(INDENT).append("}").append(NEW_LINE); // end of if
                }
                builder.append("}").append(NEW_LINE); // end of while loop
            }
        }
    }

    private List<ProjectDevice> searchGroup(ProjectDevice projectDevice) {
        Optional<List<ProjectDevice>> projectDeviceOptional = projectDeviceGroup.stream().filter(projectDeviceList -> projectDeviceList.contains(projectDevice)).findFirst();
        if (projectDeviceOptional.isEmpty()) {
            throw new IllegalStateException("Device that its value is used in the project must be exists in the device group.");
        }
        return projectDeviceOptional.get();
    }

    private String parseExpressionForParameter(Parameter parameter, Expression expression) {
        String returnValue;
        String exprStr = parseTerms(expression.getTerms());
        if (expression instanceof NumberWithUnitExpression) {
            returnValue = String.valueOf(((NumberWithUnitExpression) expression).getNumberWithUnit().getValue());
        } else if (expression instanceof CustomNumberExpression) {
            returnValue =  "constrain(" + exprStr + ", " + parameter.getMinimumValue() + "," + parameter.getMaximumValue() + ")";
        } else if (expression instanceof ValueLinkingExpression) {
            ValueLinkingExpression valueLinkingExpression = (ValueLinkingExpression) expression;
            double fromLow = valueLinkingExpression.getSourceLowValue().getValue();
            double fromHigh = valueLinkingExpression.getSourceHighValue().getValue();
            double toLow = valueLinkingExpression.getDestinationLowValue().getValue();
            double toHigh = valueLinkingExpression.getDestinationHighValue().getValue();
            returnValue = "constrain(map(" + ArduinoCodeUtility.parseValueVariableTerm(searchGroup(valueLinkingExpression.getSourceValue().getDevice())
                    , valueLinkingExpression.getSourceValue().getValue()) + ", " + fromLow + ", " + fromHigh
                    + ", " + toLow + ", " + toHigh + "), " + toLow + ", " + toHigh + ")";
        } else if (expression instanceof ProjectValueExpression) {
            ProjectValueExpression projectValueExpression = (ProjectValueExpression) expression;
            NumericConstraint valueConstraint = (NumericConstraint) projectValueExpression.getProjectValue().getValue().getConstraint();
            NumericConstraint resultConstraint = valueConstraint.intersect(parameter.getConstraint(), Function.identity());
            returnValue = "constrain(" + exprStr + ", " + resultConstraint.getMin() + ", " + resultConstraint.getMax() + ")";
        } else if (expression instanceof SimpleStringExpression) {
            returnValue = "\"" + ((SimpleStringExpression) expression).getString() + "\"";
        } else if (expression instanceof SimpleRTCExpression) {
            returnValue = exprStr;
        } else if (expression instanceof ImageExpression) {
            ProjectValue projectValue = ((ImageExpression) expression).getProjectValue();
            returnValue = ArduinoCodeUtility.parseDeviceVariableName(searchGroup(projectValue.getDevice())) + ".get"
                    + projectValue.getValue().getName().replace(" ", "_") + "()";
        } else if (expression instanceof RecordExpression) {
            returnValue = exprStr;
        } else if (expression instanceof ComplexStringExpression) {
            List<Expression> subExpression = ((ComplexStringExpression) expression).getSubExpressions();
            if (subExpression.size() == 1 && subExpression.get(0) instanceof SimpleStringExpression) {  // only one string, generate normal C string
                returnValue = "\"" + ((SimpleStringExpression) subExpression.get(0)).getString() + "\"";
            } else if (subExpression.size() == 1 && subExpression.get(0) instanceof CustomNumberExpression) {  // only one number expression
                returnValue = "String(" + parseTerms(subExpression.get(0).getTerms()) + ").c_str()";
            } else if (subExpression.stream().allMatch(e -> e instanceof SimpleStringExpression)) {     // every expression is a string so we join them
                returnValue = subExpression.stream().map(e -> ((SimpleStringExpression) e).getString())
                        .collect(Collectors.joining("", "\"", "\""));
            } else {
                List<String> subExpressionString = new ArrayList<>();
                for (Expression e : subExpression) {
                    if (e instanceof SimpleStringExpression) {
                        subExpressionString.add("\"" + ((SimpleStringExpression) e).getString() + "\"");
                    } else if (e instanceof CustomNumberExpression) {
                        subExpressionString.add("String(" + parseTerms(e.getTerms()) + ")");
                    } else {
                        throw new IllegalStateException(e.getClass().getName() + " is not supported in ComplexStringExpression");
                    }
                }
                returnValue = "(" + String.join("+", subExpressionString) + ").c_str()";
            }
        } else if (expression instanceof SimpleIntegerExpression) {
            returnValue = ((SimpleIntegerExpression) expression).getInteger().toString();
        } else {
            throw new IllegalStateException();
        }
        return returnValue;
    }

    private String parseBeginRecentSceneFinishTime(Begin begin) {
        return begin.getName().replace(" ", "_") + "_recentSceneFinishTime";
    }

//    private int parseRefreshInterval(Expression expression) {
//        NumberWithUnit interval = expression.getUserDefinedInterval();
//        if (interval.getUnit() == Unit.SECOND) {
//            return (int) (interval.getValue() * 1000.0);    // accurate down to 1 ms
//        } else if (interval.getUnit() == Unit.MILLISECOND) {
//            return (int) interval.getValue();   // fraction of a ms is discard
//        } else {
//            throw new IllegalStateException();
//        }
//    }

//    static String parseDeviceVariableName(ProjectConfiguration configuration, ProjectDevice projectDevice) {
//        if (configuration.getIdenticalDevice(projectDevice).isPresent()) {
//            return "_" + configuration.getIdenticalDevice(projectDevice).orElseThrow().getName();
//        } else if (configuration.getActualDevice(projectDevice).isPresent()) {
//            return "_" + projectDevice.getName();
//        } else {
//            throw new IllegalStateException("Actual device of " + projectDevice.getName() + " hasn't been selected!!!");
//        }
//    }

//    private String parseDeviceTaskVariableName(ProjectConfiguration configuration, ProjectDevice device) {
//        return parseDeviceVariableName(configuration, List.of(device)) + "_Task";
//    }
//
//    private String parseDeviceExpressionVariableName(ProjectConfiguration configuration, ProjectDevice device) {
//        return parseDeviceVariableName(configuration, List.of(device)) + "_Expr";
//    }

    // The required digits is at least 6 for GPS's lat, lon values.
    private static final DecimalFormat NUMBER_WITH_UNIT_DF = new DecimalFormat("0.0#####");

    private String parseTerm(Term term) {
        if (term instanceof NumberWithUnitTerm) {
            NumberWithUnitTerm term1 = (NumberWithUnitTerm) term;
            return NUMBER_WITH_UNIT_DF.format(term1.getValue().getValue());
        } else if (term instanceof OperatorTerm) {
            OperatorTerm term1 = (OperatorTerm) term;
            switch (term1.getValue()) {
                case PLUS:
                    return "+";
                case MINUS:
                    return "-";
                case MULTIPLY:
                    return "*";
                case DIVIDE:
                    return "/";
                case MOD:
                    return "%";
                case GREATER_THAN:
                    return ">";
                case LESS_THAN:
                    return "<";
                case GREATER_THAN_OR_EQUAL:
                    return ">=";
                case LESS_THAN_OR_EQUAL:
                    return "<=";
                case AND:
                    return "&&";
                case OR:
                    return "||";
                case NOT:
                    return "!";
                case OPEN_PARENTHESIS:
                    return "(";
                case CLOSE_PARENTHESIS:
                    return ")";
                case EQUAL:
                    return "==";
                case NOT_EQUAL:
                    return "!=";
                default:
                    throw new IllegalStateException("Operator [" + term1.getValue() + "] not supported");
            }
        } else if (term instanceof RTCTerm) {
            RTCTerm term1 = (RTCTerm) term;
            LocalDateTime rtc = term1.getValue().getLocalDateTime();
            return "MP_DATETIME(" + rtc.getSecond() + "," + rtc.getMinute() + "," + rtc.getHour() +  "," + rtc.getDayOfMonth() + "," + rtc.getMonth().getValue() + "," + rtc.getYear() + ")";
        } else if (term instanceof StringTerm) {
            StringTerm term1 = (StringTerm) term;
            return "\"" + term1.getValue() + "\"";
        } else if (term instanceof ValueTerm) {
            ValueTerm term1 = (ValueTerm) term;
            ProjectValue value = term1.getValue();
            return ArduinoCodeUtility.parseValueVariableTerm(searchGroup(value.getDevice()), value.getValue());
        } else if (term instanceof RecordTerm) {
            RecordTerm term1 = (RecordTerm) term;
            return "Record(" + term1.getValue().getEntryList().stream()
                    .map(entry -> "Entry(\"" + entry.getField() + "\", " + parseTerms(entry.getValue().getTerms()) + ")")
                    .collect(Collectors.joining(",")) + ")";
        } else if (term instanceof IntegerTerm) {
            return term.toString();
        } else {
            throw new IllegalStateException("Not implemented parseTerm for Term [" + term + "]");
        }
    }

    private String parseTerms(List<Term> expression) {
        return expression.stream().map(this::parseTerm).collect(Collectors.joining(" "));
    }
}
