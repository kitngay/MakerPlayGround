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

package io.makerplayground.generator.devicemapping;

public enum ProjectMappingResult {
    OK(""),
    NO_MCU_SELECTED("Controller hasn't been selected"),
    NOT_SELECT_DEVICE("Device hasn't been selected"),
    NOT_SELECT_PORT("Port hasn't been selected"),
    NO_SUPPORT_DEVICE("Can't find support device"),
    CANT_ASSIGN_PORT("Can't automatically assigned port for some devices"),
    NO_SUPPORT_CLOUD_PLATFORM("Some cloud platform is not supported for this configuration"),
    NO_CONNECTION_FOR_DEVICE("Some device cannot connect to controller.");

    private final String errorMessage;

    ProjectMappingResult(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
