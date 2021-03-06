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

package io.makerplayground.generator.upload;

import com.fazecast.jSerialComm.SerialPort;
import io.makerplayground.generator.source.SourceCodeResult;
import io.makerplayground.project.Project;
import io.makerplayground.project.ProjectConfiguration;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;

public abstract class UploadTask extends Task<UploadResult> {

    protected final Project project;
    protected final ProjectConfiguration configuration;
    protected final SourceCodeResult sourcecode;
    protected final SerialPort serialPort;
    protected final ReadOnlyStringWrapper log;

    public UploadTask(Project project, SourceCodeResult sourceCode, SerialPort serialPort) {
        this.project = project;
        this.configuration = project.getProjectConfiguration();
        this.sourcecode = sourceCode;
        this.serialPort = serialPort;
        this.log = new ReadOnlyStringWrapper();
    }

    @Override
    abstract protected UploadResult call();

    public ReadOnlyStringProperty logProperty() {
        return log.getReadOnlyProperty();
    }
}
