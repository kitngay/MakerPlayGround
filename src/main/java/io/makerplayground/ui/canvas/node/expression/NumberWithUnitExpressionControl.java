package io.makerplayground.ui.canvas.node.expression;

import io.makerplayground.device.Parameter;
import io.makerplayground.helper.NumberWithUnit;
import io.makerplayground.helper.Unit;
import io.makerplayground.project.ProjectValue;
import io.makerplayground.project.expression.*;
import io.makerplayground.ui.canvas.node.expression.numberwithunit.NumberWithUnitControl;
import io.makerplayground.ui.canvas.node.expression.custom.ChipField;
import io.makerplayground.ui.canvas.node.expression.valuelinking.ValueLinkingControl;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.List;

public abstract class NumberWithUnitExpressionControl extends HBox {

    private final Parameter parameter;
    private final List<ProjectValue> projectValues;

    private ReadOnlyObjectWrapper<Expression> expression = new ReadOnlyObjectWrapper<>();

    public NumberWithUnitExpressionControl(Parameter p, List<ProjectValue> projectValues, Expression expression) {
        this.parameter = p;
        this.projectValues = projectValues;
        this.expression.set(expression);
        initView();
    }

    private void initView() {
        getChildren().clear();
        getStylesheets().add(getClass().getResource("/css/canvas/node/expressioncontrol/NumberWithUnitExpressionControl.css").toExternalForm());

        RadioMenuItem numberRadioButton = new RadioMenuItem("Number");
        RadioMenuItem valueRadioButton = new RadioMenuItem("Value");
        RadioMenuItem customRadioButton = new RadioMenuItem("Custom");

        ToggleGroup toggleGroup = new ToggleGroup();
        toggleGroup.getToggles().addAll(numberRadioButton, valueRadioButton, customRadioButton);

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(numberRadioButton, valueRadioButton, customRadioButton);

        ImageView configButton = new ImageView(new Image(getClass().getResourceAsStream("/css/canvas/node/expressioncontrol/advance-setting-press.png")));
        configButton.setFitWidth(25);
        configButton.setPreserveRatio(true);
        configButton.setOnMousePressed(event -> contextMenu.show(configButton, Side.BOTTOM, 0, 0));

        if (getExpression() instanceof CustomNumberExpression) {
            ChipField chipField = new ChipField((CustomNumberExpression) getExpression(), projectValues);
            chipField.expressionProperty().addListener((observable, oldValue, newValue) -> expression.set(newValue));
            toggleGroup.selectToggle(customRadioButton);
            getChildren().add(chipField);
        } else if (getExpression() instanceof NumberWithUnitExpression) {
            NumberWithUnit numberWithUnit = ((NumberWithUnitExpression) getExpression()).getNumberWithUnit();
            NumberWithUnitControl numberWithUnitControl = createNumberWithUnitControl(parameter.getMinimumValue(), parameter.getMaximumValue(), parameter.getUnit(), numberWithUnit);
            numberWithUnitControl.valueProperty().addListener((observable, oldValue, newValue) -> expression.set(new NumberWithUnitExpression(newValue)));
            toggleGroup.selectToggle(numberRadioButton);
            getChildren().add(numberWithUnitControl);
        } else if (getExpression() instanceof ValueLinkingExpression || getExpression() instanceof ProjectValueExpression) {
            ValueLinkingControl valueLinkingControl = new ValueLinkingControl(getExpression(), projectValues, parameter);
            valueLinkingControl.expressionProperty().addListener((observable, oldValue, newValue) -> expression.set(newValue));
            toggleGroup.selectToggle(valueRadioButton);
            getChildren().add(valueLinkingControl);
        } else {
            throw new IllegalStateException();
        }

        getChildren().add(configButton);
        setSpacing(5);

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == numberRadioButton) {
                expression.set(new NumberWithUnitExpression(new NumberWithUnit(parameter.getMinimumValue(), parameter.getUnit().get(0))));
            } else if (newValue == valueRadioButton) {
                expression.set(new ProjectValueExpression());
            } else if (newValue == customRadioButton) {
                expression.set(new CustomNumberExpression());
            }
            initView();
        });
    }

    protected abstract NumberWithUnitControl createNumberWithUnitControl(double min, double max, List<Unit> unit, NumberWithUnit initialValue);

    public Expression getExpression() {
        return expression.get();
    }

    public ReadOnlyObjectProperty<Expression> expressionProperty() {
        return expression.getReadOnlyProperty();
    }
}