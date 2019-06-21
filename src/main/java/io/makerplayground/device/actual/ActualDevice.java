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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.makerplayground.device.generic.GenericDevice;
import lombok.Builder;
import lombok.Data;

import java.util.*;

@Data @Builder
@JsonDeserialize(using = ActualDeviceDeserializer.class)
public class ActualDevice implements Comparable<ActualDevice> {
    protected final String id;
    protected final String brand;
    protected final String model;
    protected final String url;
    protected final double width;
    protected final double height;
    protected final String pioBoardId;
    protected final DeviceType deviceType;
    protected final List<Pin> pinProvide;
    protected final List<Pin> pinConsume;
    protected final List<Pin> pinUnused;
    protected final List<Property> property;
    protected final List<Port> portProvide;
    protected final List<Port> portConsume;
    protected final CloudPlatform cloudConsume;

    /* compatibility */
    protected final Map<GenericDevice, Compatibility> compatibilityMap;

    /* cloud that provided to the system by the device */
    protected final Map<CloudPlatform, SourceCodeLibrary> cloudPlatformSourceCodeLibrary;

    /* name of the device implementation class */
    protected final Map<Platform, SourceCodeLibrary> platformSourceCodeLibrary;

    protected final List<IntegratedActualDevice> integratedDevices;

    public Optional<Property> getProperty(String name) {
        return property.stream().filter(property1 -> property1.getName().equals(name)).findFirst();
    }

    public Optional<IntegratedActualDevice> getIntegratedDevices(String name) {
        return integratedDevices.stream().filter(integratedActualDevice -> integratedActualDevice.getId().equals(name)).findFirst();
    }

    public String getMpLibrary(Platform platform) {
        SourceCodeLibrary sourceCodeLibrary = platformSourceCodeLibrary.get(platform);
        if (sourceCodeLibrary != null) {
            return sourceCodeLibrary.getClassName();
        }
        throw new IllegalStateException("The actual device [" + String.join(", ", List.of(id, brand, model)) + "] not support for platform [" + platform.getDisplayName() + "]");
    }

    public List<String> getExternalLibrary(Platform platform) {
        SourceCodeLibrary sourceCodeLibrary = platformSourceCodeLibrary.get(platform);
        if (sourceCodeLibrary != null) {
            return sourceCodeLibrary.getDependency();
        }
        throw new IllegalStateException("The actual device [" + String.join(", ", List.of(id, brand, model)) + "] not support for platform [" + platform.getDisplayName() + "]");
    }

    public String getCloudPlatformLibraryName(CloudPlatform cloudPlatform) {
        SourceCodeLibrary sourceCodeLibrary = cloudPlatformSourceCodeLibrary.get(cloudPlatform);
        if (sourceCodeLibrary != null) {
            return sourceCodeLibrary.getClassName();
        }
        throw new IllegalStateException("The actual device [" + String.join(", ", List.of(id, brand, model)) + "] not support for cloudplatform [" + cloudPlatform.getDisplayName() + "]");
    }

    public List<String> getCloudPlatformLibraryDependency(CloudPlatform cloudPlatform) {
        SourceCodeLibrary sourceCodeLibrary = cloudPlatformSourceCodeLibrary.get(cloudPlatform);
        if (sourceCodeLibrary != null) {
            return sourceCodeLibrary.getDependency();
        }
        throw new IllegalStateException("The actual device [" + String.join(", ", List.of(id, brand, model)) + "] not support for cloudplatform [" + cloudPlatform.getDisplayName() + "]");
    }

    @JsonIgnore
    public boolean isPinProviderDevice() {
        return Objects.nonNull(getPinProvide()) && !getPinProvide().isEmpty();
    }

    @JsonIgnore
    public boolean isPortProviderDevice() {
        return Objects.nonNull(getPortProvide()) && !getPortProvide().isEmpty();
    }

    @Override
    public int compareTo(ActualDevice o) {
        return (getBrand() + getModel()).compareTo(o.getBrand() + o.getModel());
    }
}
