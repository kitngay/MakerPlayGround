/*
 * Copyright (c) 2018. The Maker Playground Authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.makerplayground.device.DeviceLibrary;
import io.makerplayground.project.Project;
import io.makerplayground.ui.dialog.UnsavedDialog;
import io.makerplayground.version.ProjectVersionControl;
import io.makerplayground.version.SoftwareVersion;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Nuntipat Narkthong on 6/6/2017 AD.
 */
public class Main extends Application {

    private Toolbar toolbar;
    private ObjectProperty<Project> project;
    private File latestProjectDirectory;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // TODO: show progress indicator while loading if need
        DeviceLibrary.INSTANCE.loadDeviceFromJSON();

        toolbar = new Toolbar();
        toolbar.setOnNewButtonPressed(event -> newProject(primaryStage.getOwner()));
        toolbar.setOnLoadButtonPressed(event -> loadProject(primaryStage.getOwner()));
        toolbar.setOnSaveButtonPressed(event -> saveProject(primaryStage.getOwner()));
        toolbar.setOnSaveAsButtonPressed(event -> saveProjectAs(primaryStage.getOwner()));

        project = new SimpleObjectProperty<>(new Project());

        BorderPane borderPane = new BorderPane();
        borderPane.setTop(toolbar);
        borderPane.setCenter(new MainWindow(project.get()));

        final Scene scene = new Scene(borderPane, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/light-theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());

        ChangeListener<String> projectPathListener = (observable, oldValue, newValue) -> updatePath(primaryStage, newValue);
        project.get().filePathProperty().addListener(projectPathListener);
        updatePath(primaryStage, project.get().getFilePath());
        project.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.filePathProperty().removeListener(projectPathListener);
            }
            newValue.filePathProperty().addListener(projectPathListener);
            updatePath(primaryStage, newValue.getFilePath());
            borderPane.setCenter(new MainWindow(newValue));
        });

        // close program
        primaryStage.setOnCloseRequest(event -> {
            if (project.get().hasUnsavedModification()) {
                UnsavedDialog.Response retVal = new UnsavedDialog(scene.getWindow()).showAndGetResponse();
                if (retVal == UnsavedDialog.Response.CANCEL) {
                    event.consume();
                    return;
                } else if (retVal == UnsavedDialog.Response.SAVE) {
                    saveProject(scene.getWindow());
                }
            }

            primaryStage.close();
            Platform.exit();
            System.exit(0);
        });

        // setup keyboard shortcut for new, save and load
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.O) {
                loadProject(scene.getWindow());
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.N) {
                newProject(scene.getWindow());
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
                saveProject(scene.getWindow());
            }
        });

        primaryStage.getIcons().addAll(new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_16.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_20.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_24.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_32.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_40.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_48.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_60.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_72.png"))
                , new Image(Main.class.getResourceAsStream("/icons/taskbar/logo_taskbar_256.png")));
        primaryStage.setScene(scene);
        primaryStage.show();

        new UpdateNotifier(scene.getWindow(), getHostServices()).start();
    }

    private void updatePath(Stage stage, String path) {
        if (path.isEmpty()) {
            stage.setTitle(SoftwareVersion.CURRENT_VERSION.getBuildName() + " - Untitled Project");
        } else {
            stage.setTitle(SoftwareVersion.CURRENT_VERSION.getBuildName() + " - " + path);
        }
    }

    public void newProject(Window window) {
        if (project.get().hasUnsavedModification()) {
            UnsavedDialog.Response retVal = new UnsavedDialog(window).showAndGetResponse();
            if (retVal == UnsavedDialog.Response.CANCEL) {
                return;
            } else if (retVal == UnsavedDialog.Response.SAVE) {
                saveProject(window);
            }
        }
        project.set(new Project());
    }

    public void loadProject(Window window) {
        if (project.get().hasUnsavedModification()) {
            UnsavedDialog.Response retVal = new UnsavedDialog(window).showAndGetResponse();
            if (retVal == UnsavedDialog.Response.CANCEL) {
                return;
            } else if (retVal == UnsavedDialog.Response.SAVE) {
                saveProject(window);
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MakerPlayground Projects", "*.mp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile != null) {
            String projectVersion = ProjectVersionControl.readProjectVersion(selectedFile);
            if (ProjectVersionControl.CURRENT_VERSION.equals(projectVersion)
                    || ProjectVersionControl.isConvertibleToCurrentVersion(projectVersion)) {
                project.set(Project.loadProject(selectedFile));
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "The program does not support this previous project version.", ButtonType.OK);
                alert.showAndWait();
            }
        }
    }

    public void saveProject(Window window) {
        toolbar.setStatusMessage("Saving...");
        try {
            File selectedFile;
            if (project.get().getFilePath().isEmpty()) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save File");
                if (latestProjectDirectory != null) {
                    fileChooser.setInitialDirectory(latestProjectDirectory);
                }
                fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("MakerPlayground Projects", "*.mp"));
                fileChooser.setInitialFileName("*.mp");
                selectedFile = fileChooser.showSaveDialog(window);
            } else {
                selectedFile = new File(project.get().getFilePath());
            }

            if (selectedFile != null) {
                latestProjectDirectory = selectedFile.getParentFile();
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(selectedFile, project.get());
                project.get().setFilePath(selectedFile.getAbsolutePath());
                toolbar.setStatusMessage("Saved");
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> toolbar.setStatusMessage(""));
                    }
                }, 3000);
            } else {
                toolbar.setStatusMessage("");
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public void saveProjectAs(Window window) {
        toolbar.setStatusMessage("Saving...");
        try {
            File selectedFile;
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save File As");
            if (latestProjectDirectory != null) {
                fileChooser.setInitialDirectory(latestProjectDirectory);
            }
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("MakerPlayground Projects", "*.mp"));
            fileChooser.setInitialFileName("*.mp");
            selectedFile = fileChooser.showSaveDialog(window);

            if (selectedFile != null) {
                latestProjectDirectory = selectedFile.getParentFile();
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(selectedFile, project.get());
                project.get().setFilePath(selectedFile.getAbsolutePath());
                toolbar.setStatusMessage("Saved");
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> toolbar.setStatusMessage(""));
                    }
                }, 3000);
            } else {
                toolbar.setStatusMessage("");
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
