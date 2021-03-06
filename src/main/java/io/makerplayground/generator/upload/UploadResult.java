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

public enum UploadResult {
    OK,
    CANT_FIND_PIO,
    DEVICE_OR_PORT_MISSING,
    CANT_CREATE_PROJECT,
    CANT_GENERATE_CODE,
    CODE_ERROR,
    UNKNOWN_ERROR,
    CANT_FIND_BOARD,
    CANT_WRITE_CODE,
    MISSING_LIBRARY_DIR,
    CANT_FIND_LIBRARY,
    USER_CANCEL
}
