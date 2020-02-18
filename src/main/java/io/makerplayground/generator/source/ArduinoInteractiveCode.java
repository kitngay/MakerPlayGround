/*
 * Copyright (c) 2020. The Maker Playground Authors.
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

import io.makerplayground.device.actual.ActualDevice;
import io.makerplayground.device.actual.CloudPlatform;
import io.makerplayground.device.actual.Compatibility;
import io.makerplayground.device.generic.GenericDevice;
import io.makerplayground.device.shared.DataType;
import io.makerplayground.device.shared.Parameter;
import io.makerplayground.generator.devicemapping.ProjectLogic;
import io.makerplayground.generator.devicemapping.ProjectMappingResult;
import io.makerplayground.project.Project;
import io.makerplayground.project.ProjectConfiguration;
import io.makerplayground.project.ProjectDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.makerplayground.generator.source.ArduinoCodeUtility.*;

public class ArduinoInteractiveCode {

    final Project project;
    final ProjectConfiguration configuration;
    final StringBuilder builder = new StringBuilder();
    private final List<List<ProjectDevice>> projectDeviceGroup;

    private ArduinoInteractiveCode(Project project) {
        this.project = project;
        this.configuration = project.getProjectConfiguration();
        this.projectDeviceGroup = project.getAllDevicesGroupBySameActualDevice();
    }

    public static SourceCodeResult generateCode(Project project) {
        // Check if all used devices are assigned.
        if (ProjectLogic.validateDeviceAssignment(project) != ProjectMappingResult.OK) {
            return new SourceCodeResult(SourceCodeError.NOT_SELECT_DEVICE_OR_PORT, "-");
        }
        if (!Utility.validateDeviceProperty(project)) {
            return new SourceCodeResult(SourceCodeError.MISSING_PROPERTY, "-");   // TODO: add location
        }
        if (project.getProjectConfiguration().isUseHwSerial()) {
            return new SourceCodeResult(SourceCodeError.INTERACTIVE_MODE_NEED_HW_SERIAL, "-");
        }
        ArduinoInteractiveCode generator = new ArduinoInteractiveCode(project);
        generator.appendHeader();
        generator.appendGlobalVariable();
        generator.builder.append(getInstanceVariablesCode(project, generator.projectDeviceGroup));
        generator.builder.append(getSetupFunctionCode(project, generator.projectDeviceGroup, false));
        generator.appendProcessCommand();
        generator.appendLoopFunction();
//        System.out.println(generator.builder.toString());
        return new SourceCodeResult(generator.builder.toString());
    }

    private void appendHeader() {
        builder.append("#include \"MakerPlayground.h\"").append(NEW_LINE);

        // generate include
        Stream<String> device_libs = project.getUnmodifiableProjectDevice().stream()
                .filter(projectDevice -> configuration.getActualDevice(projectDevice).isPresent())
                .map(projectDevice -> configuration.getActualDevice(projectDevice).orElseThrow().getMpLibrary(project.getSelectedPlatform()));
        Stream<String> cloud_libs = project.getAllCloudPlatforms().stream()
                .flatMap(cloudPlatform -> Stream.of(cloudPlatform.getLibName(), project.getSelectedController().getCloudPlatformLibraryName(cloudPlatform)));
        Stream.concat(device_libs, cloud_libs).distinct().sorted().forEach(s -> builder.append(ArduinoCodeUtility.parseIncludeStatement(s)).append(NEW_LINE));
        builder.append(NEW_LINE);
    }

    private void appendGlobalVariable() {
        builder.append("uint8_t statusCode = 0;").append(NEW_LINE);
        builder.append("unsigned long lastSendTime = 0;").append(NEW_LINE);
//        builder.append("unsigned long currentTime = 0;").append(NEW_LINE);
        builder.append("const int SEND_INTERVAL = 100;").append(NEW_LINE);
        builder.append("char serialBuffer[256];").append(NEW_LINE);
        builder.append("uint8_t serialBufferIndex = 0;").append(NEW_LINE);
        builder.append("char* commandArgs[10];").append(NEW_LINE);
        builder.append(NEW_LINE);
    }

    private void appendProcessCommand() {
        builder.append("void processCommand() {").append(NEW_LINE);
        builder.append(INDENT).append("uint8_t i = 0, argsCount = 0, length = strlen(serialBuffer);").append(NEW_LINE);
        builder.append(INDENT).append("while (i < length) {").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("if (serialBuffer[i] == '\"') {").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("i++;").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("commandArgs[argsCount] = &serialBuffer[i];").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("argsCount++;").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("while (i < length && serialBuffer[i] != '\"') {").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("i++;").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("}").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("serialBuffer[i] = '\\0';").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("}").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("i++;").append(NEW_LINE);
        builder.append(INDENT).append("}").append(NEW_LINE);
        builder.append(NEW_LINE);

        boolean firstCondition = true;
        for (ProjectDevice projectDevice : project.getUnmodifiableProjectDevice()) {
            if (projectDevice.getGenericDevice().hasAction()) {
                String variableName = ArduinoCodeUtility.parseDeviceVariableName(searchGroup(projectDevice));
                builder.append(INDENT).append(firstCondition ? "if " : "else if ").append("(strcmp_P(commandArgs[0], (PGM_P) F(\"")
                        .append(projectDevice.getName()).append("\")) == 0) {").append(NEW_LINE);
                firstCondition = false;

                if (project.getProjectConfiguration().getActualDevice(projectDevice).isPresent()) {
                    ActualDevice actualDevice = project.getProjectConfiguration().getActualDevice(projectDevice).get();
                    for (GenericDevice genericDevice: actualDevice.getCompatibilityMap().keySet()) {
                        Compatibility compatibility = actualDevice.getCompatibilityMap().get(genericDevice);

                        AtomicInteger j= new AtomicInteger();
                        compatibility.getDeviceAction().forEach((action, parameterConstraintMap) -> {
                            builder.append(j.getAndIncrement() == 0 ? INDENT + INDENT + "if " : "else if ").append("(strcmp_P(commandArgs[1], (PGM_P) F(\"")
                                    .append(action.getName()).append("\")) == 0 && argsCount == ").append(action.getParameter().size() + 2)
                                    .append(") {").append(NEW_LINE);

                            if (action.getParameter().size() == 1 && action.getParameter().get(0).getDataType() == DataType.RECORD) {
                                builder.append(INDENT).append(INDENT).append(INDENT).append("Record rec;").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append("uint8_t j = 0, keyValueCount = 0, length_rec = strlen(commandArgs[2]);").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append("char* keyValues[20];").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append("while (j < length_rec && keyValueCount < 20) {").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("if (commandArgs[2][j++] == '[') {").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("keyValues[keyValueCount++] = &(commandArgs[2][j]);").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("while (j < length_rec && commandArgs[2][j] != ',') {").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("j++;").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("}").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("commandArgs[2][j++] = '\\0';").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("keyValues[keyValueCount++] = &(commandArgs[2][j]);").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("while (j < length_rec && commandArgs[2][j] != ']') {").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("j++;").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("}").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("commandArgs[2][j++] = '\\0';").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("rec.put(keyValues[keyValueCount-2], atof(keyValues[keyValueCount-1]));").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("}").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append("}").append(NEW_LINE);
                                builder.append(INDENT).append(INDENT).append(INDENT).append(variableName).append(".").append(action.getFunctionName()).append("(rec);").append(NEW_LINE);
                            } else {
                                List<String> taskParameter = new ArrayList<>();
                                for (int i=0; i<action.getParameter().size(); i++) {
                                    Parameter parameter = action.getParameter().get(i);
                                    switch (parameter.getDataType()) {
                                        case DOUBLE:
                                            taskParameter.add("atof(commandArgs[" + (i+2) + "])");
                                            break;
                                        case INTEGER:
                                            taskParameter.add("atoi(commandArgs[" + (i+2) + "])");
                                            break;
                                        default:
                                            taskParameter.add("commandArgs[" + (i+2) + "]");
                                            break;
                                    }

                                }
                                builder.append(INDENT).append(INDENT).append(INDENT).append(variableName).append(".").append(action.getFunctionName())
                                        .append("(").append(String.join(", ", taskParameter)).append(");").append(NEW_LINE);
                            }

                            builder.append(INDENT).append(INDENT).append("} ");
                        });
                    }
                }

                builder.append(NEW_LINE).append(INDENT).append("}").append(NEW_LINE);
            }
        }
        builder.append("}").append(NEW_LINE);
        builder.append(NEW_LINE);
    }

    private void appendLoopFunction() {
        builder.append("void loop() {").append(NEW_LINE);

        builder.append(INDENT).append("currentTime = millis();").append(NEW_LINE);
        builder.append(NEW_LINE);

        // allow all cloud platform maintains their own tasks (e.g. connection)
        for (CloudPlatform cloudPlatform : project.getAllCloudPlatforms()) {
            builder.append(INDENT).append(ArduinoCodeUtility.parseCloudPlatformVariableName(cloudPlatform)).append("->update(currentTime);").append(NEW_LINE);
        }

        for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
            String variableName = ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList);
            builder.append(INDENT).append(variableName).append(".update(currentTime);").append(NEW_LINE);
        }
        builder.append(NEW_LINE);

        if (!project.getUnmodifiableProjectDevice().isEmpty()) {
            builder.append(INDENT).append("if (currentTime - lastSendTime >= SEND_INTERVAL) {").append(NEW_LINE);
            for (List<ProjectDevice> projectDeviceList: projectDeviceGroup) {
                for (ProjectDevice projectDevice: projectDeviceList) {
                    if (project.getProjectConfiguration().getActualDeviceOrActualDeviceOfIdenticalDevice(projectDevice).isPresent()) {
                        ActualDevice actualDevice = project.getProjectConfiguration().getActualDeviceOrActualDeviceOfIdenticalDevice(projectDevice).get();
                        for (GenericDevice genericDevice: actualDevice.getCompatibilityMap().keySet()) {
                            if (genericDevice == projectDevice.getGenericDevice()) {
                                Compatibility compatibility = actualDevice.getCompatibilityMap().get(genericDevice);
                                if (!compatibility.getDeviceCondition().isEmpty() || !compatibility.getDeviceValue().isEmpty()) {
                                    String variableName = ArduinoCodeUtility.parseDeviceVariableName(projectDeviceList);
                                    builder.append(INDENT).append(INDENT).append("Serial.print(F(\"\\\"").append(projectDevice.getName()).append("\\\"\"));").append(NEW_LINE);
                                    // condition
                                    compatibility.getDeviceCondition().forEach((condition, parameterConstraintMap) -> {
                                        if (condition.getName().equals("Compare")) {    // TODO: compare with name is dangerous
                                            return;
                                        }
                                        builder.append(INDENT).append(INDENT).append("Serial.print(F(\" \"));").append(NEW_LINE);
                                        builder.append(INDENT).append(INDENT).append("Serial.print(").append(variableName).append(".").append(condition.getFunctionName()).append("());").append(NEW_LINE);
                                    });
                                    // value
                                    compatibility.getDeviceValue().forEach((value, constraint) -> {
                                        builder.append(INDENT).append(INDENT).append("Serial.print(F(\" \"));").append(NEW_LINE);
                                        builder.append(INDENT).append(INDENT).append("Serial.print(").append(variableName).append(".get").append(value.getName().replace(" ", "_").replace(".", "_")).append("());").append(NEW_LINE);
                                    });
                                    builder.append(INDENT).append(INDENT).append("Serial.println();").append(NEW_LINE);
                                    builder.append(NEW_LINE);
                                }
                            }
                        }
                    }
                }
            }
            builder.append(INDENT).append(INDENT).append("lastSendTime = millis();").append(NEW_LINE);
            builder.append(INDENT).append("}").append(NEW_LINE);
        }

        builder.append(INDENT).append("while (Serial.available() > 0) {").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("serialBuffer[serialBufferIndex] = Serial.read();").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("if (serialBuffer[serialBufferIndex] == '\\r' || serialBuffer[serialBufferIndex] == '\\n') {").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("serialBuffer[serialBufferIndex] = '\\0';").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("processCommand();").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("serialBufferIndex = 0;").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append(INDENT).append("break;").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("}").append(NEW_LINE);
        builder.append(INDENT).append(INDENT).append("serialBufferIndex++;").append(NEW_LINE);
        builder.append(INDENT).append("}").append(NEW_LINE);

        builder.append("}").append(NEW_LINE);
    }

    private List<ProjectDevice> searchGroup(ProjectDevice projectDevice) {
        Optional<List<ProjectDevice>> projectDeviceOptional = projectDeviceGroup.stream().filter(projectDeviceList -> projectDeviceList.contains(projectDevice)).findFirst();
        if (projectDeviceOptional.isEmpty()) {
            throw new IllegalStateException("Device that its value is used in the project must be exists in the device group.");
        }
        return projectDeviceOptional.get();
    }
}