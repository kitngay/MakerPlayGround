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

package io.makerplayground.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.makerplayground.device.GenericDeviceType;
import io.makerplayground.device.actual.ActualDevice;
import io.makerplayground.device.actual.CloudPlatform;
import io.makerplayground.device.actual.Platform;
import io.makerplayground.device.generic.GenericDevice;
import io.makerplayground.device.shared.*;
import io.makerplayground.device.shared.constraint.Constraint;
import io.makerplayground.generator.devicemapping.ProjectLogic;
import io.makerplayground.version.ProjectVersionControl;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represent a project
 */
@JsonSerialize(using = ProjectSerializer.class)
@JsonDeserialize(using = ProjectDeserializer.class)
public class Project {
    @Getter @Setter private String projectName;
    private final StringProperty filePath;
    private final ObservableList<ProjectDevice> devices;
    private final ObservableList<Scene> scenes;
    private final ObservableList<Condition> conditions;
    private final ObservableList<Delay> delays;
    private final ObservableList<Line> lines;
    private final ObservableList<Begin> begins;
    private final BooleanProperty hasDiagramError;

    @Getter private final FilteredList<ProjectDevice> sensorDevice;
    @Getter private final FilteredList<ProjectDevice> actuatorDevice;
    @Getter private final FilteredList<ProjectDevice> utilityDevice;
    @Getter private final FilteredList<ProjectDevice> cloudDevice;
    @Getter private final FilteredList<ProjectDevice> interfaceDevice;
    @Getter private final FilteredList<ProjectDevice> deviceWithAction;
    @Getter private final FilteredList<ProjectDevice> deviceWithCondition;

    @Getter private final ObservableList<ProjectDevice> unmodifiableProjectDevice;
    @Getter private final ObservableList<Scene> unmodifiableScene;
    @Getter private final ObservableList<Condition> unmodifiableCondition;
    @Getter private final ObservableList<Delay> unmodifiableDelay;
    @Getter private final ObservableList<Line> unmodifiableLine;

    private static final Pattern sceneNameRegex = Pattern.compile("Scene\\d+");
    private static final Pattern beginNameRegex = Pattern.compile("Begin\\d+");
    private static final Pattern conditionNameRegex = Pattern.compile("Condition\\d+");
    private static final Pattern delayNameRegex = Pattern.compile("Delay\\d+");

    @Getter @Setter private ProjectConfiguration projectConfiguration;
    @Getter private InteractiveModel interactiveModel;

    private final ObservableList<NodeElement> nodeError;
    private final ObservableList<Line> lineError;

    public Project() {
        this.projectName = "Untitled Project";
        this.filePath = new SimpleStringProperty("");

        this.devices = FXCollections.observableArrayList();
        this.unmodifiableProjectDevice = FXCollections.unmodifiableObservableList(devices);
        this.actuatorDevice = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().getType() == GenericDeviceType.ACTUATOR);
        this.sensorDevice = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().getType() == GenericDeviceType.SENSOR);
        this.utilityDevice = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().getType() == GenericDeviceType.UTILITY);
        this.cloudDevice = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().getType() == GenericDeviceType.CLOUD);
        this.interfaceDevice = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().getType() == GenericDeviceType.INTERFACE);
        this.deviceWithAction = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().hasAction());
        this.deviceWithCondition = new FilteredList<>(devices, projectDevice -> projectDevice.getGenericDevice().hasCondition());

        this.scenes = FXCollections.observableArrayList();
        this.conditions = FXCollections.observableArrayList();
        this.delays = FXCollections.observableArrayList();
        this.lines = FXCollections.observableArrayList();
        this.begins = FXCollections.observableArrayList();

        this.unmodifiableScene = FXCollections.unmodifiableObservableList(scenes);
        this.unmodifiableCondition = FXCollections.unmodifiableObservableList(conditions);
        this.unmodifiableDelay = FXCollections.unmodifiableObservableList(delays);
        this.unmodifiableLine = FXCollections.unmodifiableObservableList(lines);

        this.projectConfiguration = new ProjectConfiguration(Platform.ARDUINO_AVR8);
        this.interactiveModel = new InteractiveModel(this);
        this.newBegin();
        this.calculateCompatibility();

        this.nodeError = FXCollections.observableArrayList();
        this.lineError = FXCollections.observableArrayList();

        this.hasDiagramError = new SimpleBooleanProperty();
        this.hasDiagramError.bind(Bindings.size(nodeError).greaterThan(0).or(Bindings.size(lineError).greaterThan(0)));
    }

    // it is very difficult to directly clone an instance of the project class for many reasons e.g. UserSetting hold a
    // reference to ProjectDevice which need to be updated to the cloned ProjectDevice (complex mapping and searching)
    public static Project newInstance(Project project) {
        ObjectMapper mapper = new ObjectMapper();
        Project newProject = null;
        try {
            newProject = mapper.treeToValue(mapper.valueToTree(project), Project.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();    // this should not happen as we're parsing an in-memory stream
        }
        return newProject;
    }

    public BooleanProperty hasDiagramErrorProperty() {
        return hasDiagramError;
    }

    public String getFilePath() {
        return filePath.get();
    }

    public StringProperty filePathProperty() {
        return filePath;
    }

    public void setFilePath(String path) {
        filePath.set(path);
    }

    public Platform getSelectedPlatform() {
        return projectConfiguration.getPlatform();
    }

    private String getDeviceVarName(GenericDevice device) {
        return device.getName().replaceAll("[() ]", "");
    }

    private int getNextId(GenericDevice device) {
        String varName = getDeviceVarName(device);
        Pattern p = Pattern.compile(varName+"\\d+");
        return getUnmodifiableProjectDevice().stream()
                .filter(projectDevice -> p.matcher(projectDevice.getName()).matches())
                .mapToInt(value -> Integer.parseInt(value.getName().substring(varName.length())))
                .max()
                .orElse(0) + 1;
    }

    void addDevice(ProjectDevice projectDevice) {
        devices.add(projectDevice);
        this.calculateCompatibility();
    }

    public ProjectDevice addDevice(GenericDevice genericDevice) {
        String varName = getDeviceVarName(genericDevice);
        ProjectDevice projectDevice = new ProjectDevice(varName + getNextId(genericDevice), genericDevice);
        devices.add(projectDevice);
        calculateCompatibility();
        return projectDevice;
    }

    public void removeDevice(ProjectDevice genericDevice) {
        scenes.forEach(s->s.removeDevice(genericDevice));
        conditions.forEach(c->c.removeDevice(genericDevice));
        if (!devices.remove(genericDevice)) {
            throw new IllegalStateException("");
        }
        this.calculateCompatibility();
    }

    public void setPlatform(Platform platform) {
        this.projectConfiguration.setPlatform(platform);
        this.calculateCompatibility();
    }

    public Optional<Scene> getUnmodifiableScene(String name) {
        return unmodifiableScene.stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    private void addNodeElementErrorListener(NodeElement node) {
        node.errorProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != DiagramError.NONE) {
                if (!nodeError.contains(node)) {
                    nodeError.add(node);
                }
            } else {
                nodeError.remove(node);
            }
        });
        if (node.getError() != DiagramError.NONE) {
            nodeError.add(node);
        }
    }
    private void addLineErrorListener(Line line) {
        line.errorProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != DiagramError.NONE) {
                if (!lineError.contains(line)) {
                    lineError.add(line);
                }
            } else {
                lineError.remove(line);
            }
        });
        if (line.getError() != DiagramError.NONE) {
            lineError.add(line);
        }
    }

    public Scene newScene() {
        int id = scenes.stream()
                .filter(scene1 -> sceneNameRegex.matcher(scene1.getName()).matches())
                .mapToInt(scene1 -> Integer.parseInt(scene1.getName().substring(5)))
                .max()
                .orElse(0);

        Scene s = new Scene(this);
        s.setName("Scene" + (id + 1));
        addNodeElementErrorListener(s);
        scenes.add(s);
        checkAndInvalidateDiagram();
        return s;
    }

    public Scene newScene(Scene s) {
        int id = scenes.stream()
                .filter(scene1 -> sceneNameRegex.matcher(scene1.getName()).matches())
                .mapToInt(scene1 -> Integer.parseInt(scene1.getName().substring(5)))
                .max()
                .orElse(0);

        Scene newScene = new Scene(s, "Scene" + (id + 1), this);
        addNodeElementErrorListener(s);
        scenes.add(newScene);
        checkAndInvalidateDiagram();
        return newScene;
    }

    void addScene(Scene s) {
        scenes.add(s);
        addNodeElementErrorListener(s);
    }

    public void removeScene(Scene s) {
        scenes.remove(s);
        for (int i = lines.size()-1; i>=0; i--) {
            Line l = lines.get(i);
            if (l.getSource() == s || l.getDestination() == s) {
                lines.remove(l);
            }
        }
        nodeError.remove(s);
        checkAndInvalidateDiagram();
        this.calculateCompatibility();
    }

    public Optional<Condition> getUnmodifiableCondition(String name) {
        return conditions.stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    public Condition newCondition() {
        int id = conditions.stream()
                .filter(condition -> conditionNameRegex.matcher(condition.getName()).matches())
                .mapToInt(condition -> Integer.parseInt(condition.getName().substring(9)))
                .max()
                .orElse(0);

        Condition c = new Condition("Condition" + (id + 1), this);
        addNodeElementErrorListener(c);
        conditions.add(c);
        checkAndInvalidateDiagram();
        return c;
    }

    public Condition newCondition(Condition c) {
        int id = conditions.stream()
                .filter(condition -> conditionNameRegex.matcher(condition.getName()).matches())
                .mapToInt(condition -> Integer.parseInt(condition.getName().substring(9)))
                .max()
                .orElse(0);

        Condition newCondition = new Condition(c, "Condition" + (id + 1), this);
        addNodeElementErrorListener(c);
        conditions.add(newCondition);
        checkAndInvalidateDiagram();
        return newCondition;
    }

    void addCondition(Condition c) {
        conditions.add(c);
        addNodeElementErrorListener(c);
    }

    public void removeCondition(Condition c) {
        conditions.remove(c);
        for (int i = lines.size()-1; i>=0; i--) {
            Line l = lines.get(i);
            if (l.getSource() == c || l.getDestination() == c) {
                lines.remove(l);
            }
        }
        nodeError.remove(c);
        checkAndInvalidateDiagram();
        this.calculateCompatibility();
    }

    public Optional<Delay> getUnmodifiableDelay(String name) {
        return unmodifiableDelay.stream().filter(d -> d.getName().equals(name)).findFirst();
    }

    public Delay newDelay() {
        int id = delays.stream()
                .filter(delay1 -> delayNameRegex.matcher(delay1.getName()).matches())
                .mapToInt(delay1 -> Integer.parseInt(delay1.getName().substring(5)))
                .max()
                .orElse(0);

        Delay d = new Delay(this);
        d.setName("Delay" + (id + 1));
        delays.add(d);
        addNodeElementErrorListener(d);
        checkAndInvalidateDiagram();
        return d;
    }

    public Delay newDelay(Delay d) {
        int id = delays.stream()
                .filter(delay1 -> delayNameRegex.matcher(delay1.getName()).matches())
                .mapToInt(delay1 -> Integer.parseInt(delay1.getName().substring(5)))
                .max()
                .orElse(0);

        Delay newDelay = new Delay(d, "Delay" + (id + 1), this);
        delays.add(newDelay);
        addNodeElementErrorListener(d);
        checkAndInvalidateDiagram();
        return newDelay;
    }

    void addDelay(Delay d) {
        delays.add(d);
        addNodeElementErrorListener(d);
    }

    public void removeDelay(Delay d) {
        delays.remove(d);
        for (int i = lines.size()-1; i>=0; i--) {
            Line l = lines.get(i);
            if (l.getSource() == d || l.getDestination() == d) {
                lines.remove(l);
            }
        }
        nodeError.remove(d);
        checkAndInvalidateDiagram();
        this.calculateCompatibility();
    }

    public void addLine(NodeElement source, NodeElement destination) {
        // do not create new line if there existed a line with identical source and destination
        if (lines.stream().noneMatch(line1 -> (line1.getSource() == source) && (line1.getDestination() == destination))) {
            Line l = new Line(source, destination, this);
            lines.add(l);
            addLineErrorListener(l);
        }
        checkAndInvalidateDiagram();
        this.calculateCompatibility();
    }

    public void removeLine(Line l) {
        lines.remove(l);
        lineError.remove(l);
        checkAndInvalidateDiagram();
        this.calculateCompatibility();
    }

    public boolean hasLine(NodeElement source, NodeElement destination) {
        return lines.stream().anyMatch(line1 -> (line1.getSource() == source) && (line1.getDestination() == destination));
    }

    // TODO: need to get again after set
    public String getCloudPlatformParameter(CloudPlatform cloudPlatform, String parameterName) {
        var parameter = projectConfiguration.getUnmodifiableCloudParameterMap();
        if (parameter.containsKey(cloudPlatform)) {
            return parameter.get(cloudPlatform).get(parameterName);
        } else {
            return null;
        }
    }

    public void setCloudPlatformParameter(CloudPlatform cloudPlatform, String parameterName, String value) {
        projectConfiguration.setCloudPlatformParameter(cloudPlatform, parameterName, value);
    }

    public Set<CloudPlatform> getAllCloudPlatforms() {
        return devices.stream()
                .filter(projectDevice -> projectConfiguration.getActualDevice(projectDevice).isPresent())
                .filter(projectDevice -> projectConfiguration.getCloudConsume(projectDevice).isPresent())
                .map(projectDevice -> projectConfiguration.getCloudConsume(projectDevice).orElseThrow())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<CloudPlatform> getCloudPlatformUsed() {
        return getAllDeviceUsed().stream()
                .filter(projectDevice -> projectConfiguration.getActualDevice(projectDevice).isPresent())
                .filter(projectDevice -> projectConfiguration.getCloudConsume(projectDevice).isPresent())
                .map(projectDevice -> projectConfiguration.getCloudConsume(projectDevice).orElseThrow())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<ProjectValue> getAvailableValue(Set<DataType> dataType) {
        List<ProjectValue> value = new ArrayList<>();
        for (ProjectDevice projectDevice : devices) {
            for (Value v : projectDevice.getGenericDevice().getValue()) {
                if (dataType.contains(v.getType())) {
                    value.add(new ProjectValue(projectDevice, v));
                }
            }
        }
        return value;
    }

    public ActualDevice getSelectedController() {
        return projectConfiguration.getController();
    }

    public void setController(ActualDevice controller) {
        projectConfiguration.setController(controller);
    }

    public ObservableList<Begin> getBegin() { return begins; }

    public List<List<ProjectDevice>> getAllDeviceUsedGroupBySameActualDevice() {
        Set<ProjectDevice> deviceUsed = getAllDeviceUsed();
        return projectConfiguration.getDeviceMap().keySet().stream()
                .filter(projectDevice -> projectDevice != ProjectDevice.CONTROLLER)
                .map(projectDevice -> {
                    List<ProjectDevice> list = new ArrayList<>();
                    list.add(projectDevice);
                    list.addAll(projectConfiguration.getDeviceWithSameIdenticalDevice(projectDevice));
                    list.removeIf(projectDevice1 -> !deviceUsed.contains(projectDevice1));
                    return list;
                })
                .filter(projectDeviceList -> !projectDeviceList.isEmpty())
                .collect(Collectors.toList());
    }

    public List<List<ProjectDevice>> getAllDevicesGroupBySameActualDevice() {
        return projectConfiguration.getDeviceMap().keySet().stream()
                .filter(projectDevice -> projectDevice != ProjectDevice.CONTROLLER)
                .map(projectDevice -> {
                    List<ProjectDevice> list = new ArrayList<>();
                    list.add(projectDevice);
                    list.addAll(projectConfiguration.getDeviceWithSameIdenticalDevice(projectDevice));
                    return list;
                }).collect(Collectors.toList());
    }

    private List<List<ProjectDevice>> groupBySameActualDevice(Collection<ProjectDevice> devices) {
        return projectConfiguration.getDeviceMap().keySet().stream().map(projectDevice -> {
            List<ProjectDevice> list = new ArrayList<>();
            list.add(projectDevice);
            list.addAll(projectConfiguration.getDeviceWithSameIdenticalDevice(projectDevice));
            return list;
        }).collect(Collectors.toList());
    }

    public Set<ProjectDevice> getAllDeviceUsed() {
        Set<ProjectDevice> deviceUsed = new HashSet<>();
        Set<NodeElement> visited = new HashSet<>();
        Deque<NodeElement> queue = new ArrayDeque<>(this.begins);
        while(!queue.isEmpty()) {
            NodeElement current = queue.remove();
            if (current instanceof Begin) {
                // No device in Begin Scene
            } else if (current instanceof Scene) {
                Scene temp = (Scene) current;
                temp.getSetting().forEach(s->{
                    deviceUsed.add(s.getDevice());
                    deviceUsed.addAll(s.getAllValueUsed(EnumSet.allOf(DataType.class)).keySet());
                });
            } else if (current instanceof Condition) {
                Condition temp = (Condition) current;
                temp.getSetting().forEach(s->{
                    deviceUsed.add(s.getDevice());
                    deviceUsed.addAll(s.getAllValueUsed(EnumSet.allOf(DataType.class)).keySet());
                });
            }
            visited.add(current);
            Set<NodeElement> unvisitedAdj = lines.stream()
                    .filter(l->l.getSource() == current)
                    .map(Line::getDestination)
                    .dropWhile(visited::contains)
                    .collect(Collectors.toSet());
            queue.addAll(unvisitedAdj);
        }
        return deviceUsed;
    }

    public Set<ProjectDevice> getAllDeviceUnused() {
        Set<ProjectDevice> devicesNotUsed = new HashSet<>(this.getUnmodifiableProjectDevice());
        devicesNotUsed.removeAll(this.getAllDeviceUsed());
        return devicesNotUsed;
    }

    public Map<ProjectDevice, Set<Value>> getAllValueUsedMap(Set<DataType> dataType) {
        HashMap<ProjectDevice, Set<Value>> allValueUsed = new HashMap<>();
        Set<NodeElement> visited = new HashSet<>();
        Queue<NodeElement> queue = new LinkedList<>(this.begins);
        while(!queue.isEmpty()) {
            NodeElement current = queue.remove();
            if (current instanceof Begin) {
                // No Value in Begin Scene
            } else if (current instanceof Scene) {
                Scene temp = (Scene) current;
                temp.getSetting().forEach(s-> s.getAllValueUsed(dataType).forEach((key, value) -> {
                    allValueUsed.putIfAbsent(key, new HashSet<>());
                    allValueUsed.get(key).addAll(value);
                }));
            } else if (current instanceof Condition) {
                Condition temp = (Condition) current;
                temp.getSetting().forEach(s-> s.getAllValueUsed(dataType).forEach((key, value) -> {
                    allValueUsed.putIfAbsent(key, new HashSet<>());
                    allValueUsed.get(key).addAll(value);
                }));
            }
            visited.add(current);
            Set<NodeElement> unvisitedAdj = lines.stream()
                    .filter(l->l.getSource() == current)
                    .map(Line::getDestination)
                    .dropWhile(visited::contains)
                    .collect(Collectors.toSet());
            queue.addAll(unvisitedAdj);
        }
        return allValueUsed;
    }

    public boolean hasUnsavedModification() {
        if (filePath.get().isEmpty()) {
            // a hack way to check for project modification in case that it hasn't been saved
            return !(projectConfiguration.getPlatform() == Platform.ARDUINO_AVR8
                    && projectConfiguration.getController() == null
                    && devices.isEmpty()
                    && scenes.isEmpty()
                    && conditions.isEmpty()
                    && delays.isEmpty()
                    && lines.isEmpty()
                    && begins.size() == 1
                    && begins.get(0).getTop() == 200
                    && begins.get(0).getLeft() == 20); // begin hasn't been moved
        } else {
            ObjectMapper mapper = new ObjectMapper();
            String newContent;
            try {
                newContent = mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return true;
            }

            String oldContent;
            try {
                oldContent = new String(Files.readAllBytes(Path.of(filePath.get())));
            } catch (IOException e) {
                return true;
            }

            return !oldContent.equals(newContent);
        }
    }

    public static Optional<Project> loadProject(File f) {
        if (f.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                String projectVersion = ProjectVersionControl.readProjectVersion(f);
                if (ProjectVersionControl.canOpen(projectVersion)) {
                    Project project = mapper.readValue(f, Project.class);
                    project.setFilePath(f.getAbsolutePath());
                    return Optional.of(project);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    public boolean isNameDuplicate(String newName) {
        for (ProjectDevice projectDevice : this.getUnmodifiableProjectDevice()) {
            if (projectDevice.getName().equals(newName)) {
                return true;
            }
        }
        return false;
    }

    private Map<List<Line>, DiagramError> diagramError = Collections.emptyMap();

    private void checkAndInvalidateDiagram() {
        Map<List<Line>, DiagramError> error = new HashMap<>();

        // Reassign root to all scene and conditions
        getUnmodifiableScene().forEach(scene -> scene.setRoot(null));
        getUnmodifiableCondition().forEach(condition -> condition.setRoot(null));
        getUnmodifiableDelay().forEach(delay -> delay.setRoot(null));
        getBegin().forEach(begin -> begin.setRoot(begin));
        getBegin().forEach(this::traverseAndSetRoot);

        Map<NodeElement, List<Line>> lineFromSource = this.lines.stream().collect(Collectors.groupingBy(Line::getSource));

        for (NodeElement nodeElement : lineFromSource.keySet()) {
            List<Line> lines = lineFromSource.get(nodeElement);

            // indicate error if there are lines connect to multiple scenes from any node
            List<Line> lineToScene = lines.stream().filter(line1 -> line1.getDestination() instanceof Scene).collect(Collectors.toList());
            if (lineToScene.size() > 1) {
                error.put(lineToScene, DiagramError.DIAGRAM_MULTIPLE_SCENE);
            }

            // indicate error if there are lines connect to multiple delays from any node
            List<Line> lineToDelay = lines.stream().filter(line1 -> line1.getDestination() instanceof Delay).collect(Collectors.toList());
            if (lineToDelay.size() > 1) {
                error.put(lineToDelay, DiagramError.DIAGRAM_MULTIPLE_DELAY);
            }

            // indicate error if the current node is a condition and there is another condition in the list of the adjacent node
            List<Line> lineToCondition = lines.stream().filter(line1 -> line1.getDestination() instanceof Condition).collect(Collectors.toList());
//            if ((nodeElement instanceof Condition) && !lineToCondition.isEmpty()) {
//                error.put(lineToCondition, DiagramError.DIAGRAM_CHAIN_CONDITION);
//            }

            // indicate error if the current node is connected to both scene and condition
            if (!lineToScene.isEmpty() && !lineToCondition.isEmpty()) {
                error.put(lineToCondition, DiagramError.DIAGRAM_CONDITION_IGNORE);
            }

            // indicate error if the current node is connected to both scene and delay
            if (!lineToScene.isEmpty() && !lineToDelay.isEmpty()) {
                error.put(lineToDelay, DiagramError.DIAGRAM_DELAY_IGNORE);
            }

            // indicate error if the there are lines connecting between node with different root (i.e. there shouldn't be any link between task)
            if (!lines.stream().allMatch(line1 -> line1.getDestination().getRoot() == nodeElement.getRoot())) {
                error.put(lines, DiagramError.DIAGRAM_MULTIPLE_BEGIN);
            }
        }

        diagramError = Collections.unmodifiableMap(error);

        // invalidate every lines
        lines.forEach(Line::invalidate);
    }

    private Set<NodeElement> getNextNodeElements(NodeElement from) {
        return getUnmodifiableLine().stream().filter(line1 -> line1.getSource() == from).map(Line::getDestination).collect(Collectors.toSet());
    }

    private void traverseAndSetRoot(Begin from) {
        Set<NodeElement> visited = new HashSet<>();
        Deque<NodeElement> remainingNodes = new ArrayDeque<>(getNextNodeElements(from));
        while(!remainingNodes.isEmpty()) {
            NodeElement node = remainingNodes.removeFirst();
            if (visited.contains(node)) {
                continue;
            }
            visited.add(node);

            node.setRoot(from);

            Set<NodeElement> nextNodes = getNextNodeElements(node);
            nextNodes.removeAll(visited);
            remainingNodes.addAll(nextNodes);
        }
    }

    public Map<List<Line>, DiagramError> getDiagramStatus() {
        return diagramError;
    }

    public void removeBegin(Begin begin) {
        if (begins.size() > 1) {
            begins.remove(begin);
            for (int i = lines.size()-1; i>=0; i--) {
                Line l = lines.get(i);
                if (l.getSource() == begin || l.getDestination() == begin) {
                    lines.remove(l);
                }
            }
            checkAndInvalidateDiagram();
            this.calculateCompatibility();
        }
    }

    public Begin newBegin() {
        int id = begins.stream()
                .filter(begin1 -> beginNameRegex.matcher(begin1.getName()).matches())
                .mapToInt(begin1 -> Integer.parseInt(begin1.getName().substring(5)))
                .max()
                .orElse(0);

        Begin begin = new Begin(this);
        begin.setName("Begin" + (id + 1));
        begins.add(begin);
        checkAndInvalidateDiagram();
        return begin;
    }

    public Optional<Begin> getBegin(String name) {
        return begins.stream().filter(b -> name.equals(b.getName())).findFirst();
    }

    public void addBegin(Begin begin) {
        begins.add(begin);
    }

    public void calculateCompatibility() {
        Map<ProjectDevice, Map<Action, Map<Parameter, Constraint>>> actionCompatibility = new HashMap<>();
        Map<ProjectDevice, Map<io.makerplayground.device.shared.Condition, Map<Parameter, Constraint>>> conditionCompatibility = new HashMap<>();
        Map<ProjectDevice, Set<Value>> valueCompatibility = new HashMap<>();
        Set<NodeElement> visited = new HashSet<>();
        Deque<NodeElement> queue = new ArrayDeque<>(this.begins);
        while(!queue.isEmpty()) {
            NodeElement current = queue.remove();
            if (current instanceof Scene) {
                Scene temp = (Scene) current;
                temp.getSetting().forEach(s->{
                    ProjectDevice projectDevice = s.getDevice();
                    if (!actionCompatibility.containsKey(projectDevice)) {
                        Map<Action, Map<Parameter, Constraint>> actionParameterMap = new TreeMap<>(Comparator.comparing(Action::getName));
                        actionCompatibility.put(projectDevice, actionParameterMap);
                    }
                    s.getParameterMap().forEach((parameter, expression) -> {
                        Action action = s.getAction();
                        if (!actionCompatibility.get(projectDevice).containsKey(action)){
                            actionCompatibility.get(projectDevice).put(action, new TreeMap<>(Comparator.comparing(Parameter::getName)));
                        }
                        if (!actionCompatibility.get(projectDevice).get(action).containsKey(parameter)) {
                            actionCompatibility.get(projectDevice).get(action).put(parameter, ProjectLogic.extractConstraint(parameter, expression));
                        } else {
                            Constraint oldConstraint = actionCompatibility.get(projectDevice).get(action).get(parameter);
                            Constraint newConstraint = oldConstraint.union(ProjectLogic.extractConstraint(parameter, expression));
                            actionCompatibility.get(projectDevice).get(action).put(parameter, newConstraint);
                        }
                    });
                    s.getAllValueUsed().forEach((projectDevice1, values) -> {
                        if (valueCompatibility.containsKey(projectDevice1)) {
                            valueCompatibility.get(projectDevice1).addAll(values);
                        }
                        else {
                            valueCompatibility.put(projectDevice1, new HashSet<>(values));
                        }
                    });
                });
            } else if (current instanceof Condition) {
                Condition temp = (Condition) current;
                temp.getSetting().forEach(s->{
                    ProjectDevice projectDevice = s.getDevice();
                    if (!conditionCompatibility.containsKey(projectDevice)) {
                        Map<io.makerplayground.device.shared.Condition, Map<Parameter, Constraint>> conditionParameterMap = new TreeMap<>(Comparator.comparing(io.makerplayground.device.shared.Condition::getName));
                        conditionCompatibility.put(projectDevice, conditionParameterMap);
                    }
                    s.getParameterMap().forEach((parameter, expression) -> {
                        var condition = s.getCondition();
                        if (!conditionCompatibility.get(projectDevice).containsKey(condition)){
                            conditionCompatibility.get(projectDevice).put(condition, new TreeMap<>(Comparator.comparing(Parameter::getName)));
                        }
                        if (!conditionCompatibility.get(projectDevice).get(condition).containsKey(parameter)) {
                            conditionCompatibility.get(projectDevice).get(condition).put(parameter, ProjectLogic.extractConstraint(parameter, expression));
                        } else {
                            Constraint oldConstraint = conditionCompatibility.get(projectDevice).get(condition).get(parameter);
                            Constraint newConstraint = oldConstraint.union(ProjectLogic.extractConstraint(parameter, expression));
                            conditionCompatibility.get(projectDevice).get(condition).put(parameter, newConstraint);
                        }
                    });
                    s.getAllValueUsed().forEach((projectDevice1, values) -> {
                        if (valueCompatibility.containsKey(projectDevice1)) {
                            valueCompatibility.get(projectDevice1).addAll(values);
                        }
                        else {
                            valueCompatibility.put(projectDevice1, new HashSet<>(values));
                        }
                    });
                });
            }
            visited.add(current);
            Set<NodeElement> unvisitedAdj = lines.stream()
                    .filter(l->l.getSource() == current)
                    .map(Line::getDestination)
                    .dropWhile(visited::contains)
                    .collect(Collectors.toSet());
            queue.addAll(unvisitedAdj);
        }

        projectConfiguration.updateCompatibility(actionCompatibility, conditionCompatibility, valueCompatibility, this.devices);
    }

    public Project.SetNameResult setProjectDeviceName(ProjectDevice projectDevice, String newName) {
        if (projectDevice.getName().equals(newName)) {
            return SetNameResult.OK;
        }
        if (!newName.matches("^[a-zA-Z0-9_]+")){
            return SetNameResult.INCORRECT_PATTERN;
        }
        if (isNameDuplicate(newName)) {
            return SetNameResult.DUPLICATE_NAME;
        }
        projectDevice.setName(newName);
        calculateCompatibility();
        return SetNameResult.OK;
    }

    @RequiredArgsConstructor
    public enum SetNameResult {
        OK(""),
        DUPLICATE_NAME("Name has been used by another device"),
        INCORRECT_PATTERN("Device name must contain only a-z, A-Z and 0-9");

        @Getter private final String errorMessage;
    }
}
