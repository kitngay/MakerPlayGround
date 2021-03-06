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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.makerplayground.device.generic.GenericDevice;
import javafx.beans.property.SimpleStringProperty;
import lombok.*;

import java.util.Comparator;

@JsonSerialize (using = ProjectDeviceSerializer.class)
@JsonDeserialize (using = ProjectDeviceDeserializer.class)
@Data @AllArgsConstructor
public class ProjectDevice implements Comparable<ProjectDevice>{

    public static final Comparator<ProjectDevice> NAME_COMPARATOR = Comparator.comparing(ProjectDevice::getName);

    @JsonIgnore
    public static final ProjectDevice CONTROLLER = new ProjectDevice("Controller", null);

    @JsonIgnore @Getter(AccessLevel.NONE)
    private final SimpleStringProperty nameProperty = new SimpleStringProperty();

    @Setter(AccessLevel.NONE) private final GenericDevice genericDevice;

    public ProjectDevice(String name, GenericDevice genericDevice) {
        this.nameProperty.set(name);
        this.genericDevice = genericDevice;
    }

    @Override
    public int compareTo(ProjectDevice o) {
        return NAME_COMPARATOR.compare(this, o);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ProjectDevice) && getName().equals(((ProjectDevice) obj).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    public SimpleStringProperty NameProperty() {
        return nameProperty;
    }

    public String getName() {
        return nameProperty.get();
    }

    void setName(String name) {
        nameProperty.set(name);
    }
}
