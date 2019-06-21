/*
 * Copyright 2017 The Maker Playground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.makerplayground.project;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.makerplayground.device.shared.*;
import io.makerplayground.device.shared.Condition;
import io.makerplayground.project.expression.Expression;
import io.makerplayground.project.expression.NumberInRangeExpression;
import io.makerplayground.project.expression.RecordExpression;
import io.makerplayground.project.term.Term;
import io.makerplayground.project.term.ValueTerm;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.Getter;

import java.util.*;


/**
 *
 */
@JsonSerialize(using = UserSettingSerializer.class)
public class UserSetting {
    public enum Type {
        SCENE, CONDITION
    }

    @Getter private final ProjectDevice device;
    private final ReadOnlyObjectWrapper<Type> type;
    private final ReadOnlyObjectWrapper<Action> action;
    private final ReadOnlyObjectWrapper<Condition> condition;
    @Getter private final ObservableMap<Parameter, Expression> valueMap;
    @Getter private final ObservableMap<Value, Expression> expression;
    @Getter private final ObservableMap<Value, Boolean> expressionEnable;

    private UserSetting(ProjectDevice device) {
        this.device = device;
        this.type = new ReadOnlyObjectWrapper<>();
        this.action = new ReadOnlyObjectWrapper<>();
        this.condition = new ReadOnlyObjectWrapper<>();
        this.valueMap = FXCollections.observableHashMap();
        this.expression = FXCollections.observableHashMap();
        this.expressionEnable = FXCollections.observableHashMap();

        this.action.addListener((observable, oldValue, newValue) -> {
            valueMap.clear();
            for (Parameter param : newValue.getParameter()) {
                valueMap.put(param, Expression.fromDefaultParameter(param));
            }
        });

        this.condition.addListener((observable, oldValue, newValue) -> {
            valueMap.clear();
            for (Parameter param : newValue.getParameter()) {
                valueMap.put(param, Expression.fromDefaultParameter(param));
            }
        });

        // Initialize expression list
        // TODO: Expression is not required to be added in Scene
        for (Value v : device.getGenericDevice().getValue()) {
            if (v.getType() == DataType.DOUBLE || v.getType() == DataType.INTEGER) {
                expression.put(v, new NumberInRangeExpression(device, v));
            }
            expressionEnable.put(v, false);
        }
    }

    UserSetting(ProjectDevice device, Action supportingAction) {
        this(device);
        this.type.set(Type.SCENE);
        this.action.set(supportingAction);
    }

    UserSetting(ProjectDevice device, Condition supportingCondition) {
        this(device);
        this.type.set(Type.CONDITION);
        this.condition.set(supportingCondition);
    }

    UserSetting(ProjectDevice device, Action action, Map<Parameter, Expression> valueMap, Map<Value, Expression> expression, Map<Value, Boolean> enable) {
        this(device, action);

        // replace all default map values with these maps
        this.valueMap.putAll(valueMap);
        this.expression.putAll(expression);
        this.expressionEnable.putAll(enable);
    }

    UserSetting(ProjectDevice device, Condition condition, Map<Parameter, Expression> valueMap, Map<Value, Expression> expression, Map<Value, Boolean> enable) {
        this(device, condition);

        // replace all default map values with these maps
        this.valueMap.putAll(valueMap);
        this.expression.putAll(expression);
        this.expressionEnable.putAll(enable);
    }

    UserSetting(UserSetting u) {
        this(u.device);
        if (u.type.get() == Type.SCENE) {
            this.action.set(u.action.get());
        } else if(u.type.get() == Type.CONDITION) {
            this.condition.set(u.condition.get());
        }

        // replace values by the deepCopy version
        for (var entry: u.valueMap.entrySet()) {
            this.valueMap.put(entry.getKey(), entry.getValue().deepCopy());
        }
        for (var entry : u.expression.entrySet()) {
            this.expression.put(entry.getKey(), entry.getValue().deepCopy());
        }
        this.expressionEnable.putAll(u.expressionEnable);
    }

    private void initValueMap() {
        valueMap.clear();
        for (Parameter param : action.get().getParameter()) {
            valueMap.put(param, Expression.fromDefaultParameter(param));
        }
    }

    public Action getAction() {
        return action.get();
    }

    public Condition getCondition() {
        return condition.get();
    }

    public void setAction(Action action) {
        this.action.set(action);
        initValueMap();
    }

    public void setCondition(Condition condition) {
        this.condition.set(condition);
        initValueMap();
    }

    public ReadOnlyObjectProperty<Action> actionProperty() {
        return action.getReadOnlyProperty();
    }

    public Map<ProjectDevice, Set<Value>> getAllValueUsed(Set<DataType> dataType) {
        Map<ProjectDevice, Set<Value>> result = new HashMap<>();

        // list value use in parameters ex. show value of 7-Segment / LCD
        for (Map.Entry<Parameter, Expression> entry : valueMap.entrySet()) {
            Expression exp = entry.getValue();
            for (Term term: exp.getTerms()) {
                if (term instanceof ValueTerm) {
                    ProjectValue projectValue = ((ValueTerm) term).getValue();
                    if (projectValue != null && dataType.contains(projectValue.getValue().getType())) {
                        ProjectDevice projectDevice = projectValue.getDevice();
                        if (result.containsKey(projectDevice)) {
                            result.get(projectDevice).add(projectValue.getValue());
                        } else {
                            result.put(projectDevice, new HashSet<>(Collections.singletonList(projectValue.getValue())));
                        }
                    }
                }
            }
            if (exp instanceof RecordExpression) {
                ((RecordExpression) exp).getRecord().getEntryList().stream().flatMap(recordEntry -> recordEntry.getValue().getTerms().stream()).filter(term -> term instanceof ValueTerm).forEach(term -> {
                    ProjectValue projectValue = ((ValueTerm) term).getValue();
                    if (projectValue != null) {
                        ProjectDevice projectDevice = projectValue.getDevice();
                        if (result.containsKey(projectDevice)) {
                            result.get(projectDevice).add(projectValue.getValue());
                        } else {
                            result.put(projectDevice, new HashSet<>(Collections.singletonList(projectValue.getValue())));
                        }
                    }
                });
            }
        }

        // list value use in every enable expression in a condition
        for (Value v : expression.keySet()) {
            // skip if this expression is disabled
            if (expressionEnable.get(v)) {
                Set<ProjectValue> valueUsed = expression.get(v).getValueUsed();
                for (ProjectValue pv : valueUsed) {
                    if (result.containsKey(pv.getDevice())) {
                        result.get(pv.getDevice()).add(pv.getValue());
                    } else {
                        result.put(pv.getDevice(), new HashSet<>(Set.of(pv.getValue())));
                    }
                }
            }
        }

        return result;
    }

    public boolean isDataBindingUsed() {
        return getValueMap().values().stream()
                .anyMatch(expression1 -> (expression1.getRefreshInterval() != Expression.RefreshInterval.ONCE));
    }

    public boolean isDataBindingUsed(Parameter p) {
        return valueMap.get(p).getRefreshInterval() != Expression.RefreshInterval.ONCE;
    }

    public long getNumberOfDatabindParams() {
        return getValueMap().keySet().stream()
                .filter(parameter -> (parameter.getDataType() == DataType.DOUBLE)
                        || (parameter.getDataType() == DataType.INTEGER))
                .count();
    }
}
