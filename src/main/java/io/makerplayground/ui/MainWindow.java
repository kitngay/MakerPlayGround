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

package io.makerplayground.ui;

import com.fazecast.jSerialComm.SerialPort;
import io.makerplayground.project.Project;
import io.makerplayground.ui.canvas.CanvasView;
import io.makerplayground.ui.canvas.CanvasViewModel;
import io.makerplayground.ui.dialog.DeviceMonitor;
import javafx.application.HostServices;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class MainWindow extends BorderPane {

    private final HostServices hostServices;

    private Project currentProject;
    private ReadOnlyObjectProperty<SerialPort> serialPort;

    private Node diagramEditor;
    private DeviceTab deviceTab;
    private DeviceMonitor deviceMonitor;

    private final BooleanProperty diagramEditorShowing;
    private final BooleanProperty deviceConfigShowing;
    private final BooleanProperty deviceMonitorShowing;

    public MainWindow(ObjectProperty<Project> project, ReadOnlyObjectProperty<SerialPort> serialPort, HostServices hostServices) {
        this.currentProject = project.get();
        this.serialPort = serialPort;
        this.hostServices = hostServices;

        diagramEditor = initDiagramEditor();
        deviceTab = new DeviceTab(project.get(), hostServices);
        deviceMonitor = new DeviceMonitor();

        diagramEditorShowing = new SimpleBooleanProperty();
        diagramEditorShowing.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                setCenter(diagramEditor);
            }
        });
        deviceConfigShowing = new SimpleBooleanProperty();
        deviceConfigShowing.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                deviceTab.refreshConfigDevicePane();
                setCenter(deviceTab);
            }
        });
        deviceMonitorShowing = new SimpleBooleanProperty();
        deviceMonitorShowing.addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                deviceMonitor.closePort();
            } else {
                // TODO: handle the case when initialize failed by switch to the old tab
                deviceMonitor.initialize(serialPort.get());
                setCenter(deviceMonitor);
            }
        });

        project.addListener((observable, oldValue, newValue) -> {
            currentProject = newValue;

            diagramEditor = initDiagramEditor();
            deviceTab = new DeviceTab(project.get(), hostServices);
            deviceMonitor.closePort();
            deviceMonitor = new DeviceMonitor();

            if (diagramEditorShowing.get()) {
                setCenter(diagramEditor);
            } else if (deviceConfigShowing.get()) {
                setCenter(deviceTab);
            } else {
                setCenter(deviceMonitor);
            }
        });
    }

    public boolean isDiagramEditorShowing() {
        return diagramEditorShowing.get();
    }

    public BooleanProperty diagramEditorShowingProperty() {
        return diagramEditorShowing;
    }

    public boolean isDeviceConfigShowing() {
        return deviceConfigShowing.get();
    }

    public BooleanProperty deviceConfigShowingProperty() {
        return deviceConfigShowing;
    }

    public boolean isDeviceMonitorShowing() {
        return deviceMonitorShowing.get();
    }

    public BooleanProperty deviceMonitorShowingProperty() {
        return deviceMonitorShowing;
    }

    private Node initDiagramEditor() {
        CanvasViewModel canvasViewModel = new CanvasViewModel(currentProject);
        return new CanvasView(canvasViewModel);
    }
}
