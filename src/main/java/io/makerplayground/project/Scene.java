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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.makerplayground.device.shared.Parameter;
import io.makerplayground.project.expression.Expression;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;

/**
 *
 */
@JsonSerialize(using = SceneSerializer.class)
public class Scene extends NodeElement {

    private final ObservableList<UserSetting> setting;

    Scene(Project project) {
        super(20, 20, 205, 124, project);

        this.name = "";
        // fire update event when actionProperty is invalidated / changed
        this.setting = FXCollections.observableArrayList(item -> new Observable[]{item.actionProperty()});
        invalidate();
    }

    Scene(double top, double left, double width, double height
            , String name, List<UserSetting> setting, Project project) {
        // TODO: ignore width and height field to prevent line from drawing incorrectly when read file from old version as scene can't be resized anyway
        super(top, left, 205, 124, project);
        this.name = name;
        this.setting = FXCollections.observableArrayList(setting);
        invalidate();
    }

    Scene(Scene s, String name, Project project) {
        super(s.getTop(), s.getLeft(), s.getWidth(), s.getHeight(), project);
        this.name = name;
        this.setting = FXCollections.observableArrayList(item -> new Observable[]{item.actionProperty()});
        for (UserSetting u : s.setting) {
            this.setting.add(new UserSetting(u));
        }
        invalidate();
    }

    public void addDevice(ProjectDevice device) {
        if (device.getGenericDevice().getAction().isEmpty()) {
            throw new IllegalStateException(device.getGenericDevice().getName() + " needs to have action.");
        } else {
            setting.add(new UserSetting(device, device.getGenericDevice().getAction().get(0)));
        }
        invalidate();
    }

    public void removeDevice(ProjectDevice device) {
        for (int i = setting.size() - 1; i >= 0; i--) {
            if (setting.get(i).getDevice() == device) {
                setting.remove(i);
            }
        }
        for (UserSetting userSetting : setting) {
            for (Parameter parameter : userSetting.getParameterMap().keySet()) {
                Expression expression = userSetting.getParameterMap().get(parameter);
                if (expression.getTerms().stream().anyMatch(term -> term.getValue() instanceof ProjectValue
                        && (((ProjectValue) term.getValue()).getDevice() == device))) {
                    userSetting.getParameterMap().replace(parameter, Expression.fromDefaultParameter(parameter));
                }
            }
        }
        invalidate();
    }

    @Override
    public void setName(String name) {
        this.name = name;
        invalidate();
        // invalidate other scene as every scene needs to check for duplicate name
        for (Scene s : project.getUnmodifiableScene()) {
            s.invalidate();
        }
    }

    public ObservableList<UserSetting> getSetting() {
        return setting;
    }

    public void removeUserSetting(UserSetting userSetting) {
        this.setting.remove(userSetting);
    }

    @Override
    protected DiagramError checkError() {
        // name should contain only english alphabets and an underscore and it should not be empty
        if (!name.matches("\\w[\\w|\\s]*")) {
            return DiagramError.SCENE_INVALID_NAME;
        }

        // name should be unique
        for (Scene s : project.getUnmodifiableScene()) {
            if ((this != s) && name.equals(s.name)) {
                return DiagramError.SCENE_DUPLICATE_NAME;
            }
        }

        // parameter should not be null and should be valid
        if (setting.stream().flatMap(userSetting -> userSetting.getParameterMap().values().stream())
                .anyMatch(o -> Objects.isNull(o) || !o.isValid())) {
            return DiagramError.SCENE_INVALID_PARAM;
        }

        return DiagramError.NONE;
    }
}
