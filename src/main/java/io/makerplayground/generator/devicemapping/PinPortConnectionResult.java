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

import io.makerplayground.project.PinPortConnection;
import lombok.Data;

import java.util.List;

@Data
public class PinPortConnectionResult {
    private final PinPortConnectionResultStatus status;
    private final List<PinPortConnection> connections;

    public static PinPortConnectionResult ERROR = new PinPortConnectionResult(PinPortConnectionResultStatus.ERROR, null);
}