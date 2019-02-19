package io.makerplayground.ui.canvas.node.expression;

import io.makerplayground.device.shared.DataType;
import io.makerplayground.device.shared.NumberWithUnit;
import io.makerplayground.device.shared.Parameter;
import io.makerplayground.device.shared.Unit;
import io.makerplayground.device.shared.constraint.CategoricalConstraint;
import io.makerplayground.project.ProjectValue;
import io.makerplayground.project.expression.Expression;
import io.makerplayground.project.expression.ImageExpression;
import io.makerplayground.project.expression.ProjectValueExpression;
import io.makerplayground.project.expression.SimpleStringExpression;
import io.makerplayground.ui.canvas.node.expression.numberwithunit.NumberWithUnitControl;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.EnumSet;
import java.util.List;

public class StringExpressionControl extends HBox {

    private final Parameter parameter;
    private final List<ProjectValue> projectValues;

    private ReadOnlyObjectWrapper<Expression> expression = new ReadOnlyObjectWrapper<>();

    public StringExpressionControl(Parameter p, List<ProjectValue> projectValues, Expression expression) {
        this.parameter = p;
        this.projectValues = projectValues;
        this.expression.set(expression);
        initView();
    }

    private void initView() {
        getChildren().clear();
        getStylesheets().add(getClass().getResource("/css/canvas/node/expressioncontrol/StringExpressionControl.css").toExternalForm());

        RadioMenuItem numberRadioButton = new RadioMenuItem("Text");
        RadioMenuItem valueRadioButton = new RadioMenuItem("Value");

        ToggleGroup toggleGroup = new ToggleGroup();
        toggleGroup.getToggles().addAll(numberRadioButton, valueRadioButton);

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(numberRadioButton, valueRadioButton);

        ImageView configButton = new ImageView(new Image(getClass().getResourceAsStream("/css/canvas/node/expressioncontrol/advance-setting-press.png")));
        configButton.setFitWidth(25);
        configButton.setPreserveRatio(true);
        configButton.setStyle("-fx-cursor:hand;");
        configButton.setOnMousePressed(event -> contextMenu.show(configButton, Side.BOTTOM, 0, 0));

        if (getExpression() instanceof SimpleStringExpression) {
            TextField textField = new TextField();
            textField.textProperty().addListener((observable, oldValue, newValue) -> expression.set(new SimpleStringExpression(newValue)));
            textField.setText(((SimpleStringExpression) getExpression()).getString());
            toggleGroup.selectToggle(numberRadioButton);
            getChildren().add(textField);
        } else if (getExpression() instanceof ProjectValueExpression) {
            ComboBox<ProjectValue> comboBox = new ComboBox<>(FXCollections.observableArrayList(projectValues));
            comboBox.setCellFactory(param -> new ListCell<>(){
                @Override
                protected void updateItem(ProjectValue item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText("");
                    } else {
                        setText(item.getDevice().getName() + "'s " + item.getValue().getName());
                    }
                }
            });
            comboBox.setButtonCell(new ListCell<>(){
                @Override
                protected void updateItem(ProjectValue item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText("");
                    } else {
                        setText(item.getDevice().getName() + "'s " + item.getValue().getName());
                    }
                }
            });
            comboBox.valueProperty().addListener((observable, oldValue, newValue) -> expression.set(new ProjectValueExpression(newValue)));
            ProjectValue projectValue = ((ProjectValueExpression) getExpression()).getProjectValue();
            if (projectValue != null) {
                comboBox.getSelectionModel().select(projectValue);
            }
            getChildren().add(comboBox);
        } else {
            throw new IllegalStateException();
        }

        getChildren().add(configButton);
        setSpacing(5);

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == numberRadioButton) {
                expression.set(new SimpleStringExpression(""));
            } else if (newValue == valueRadioButton) {
                expression.set(new ProjectValueExpression());
            }
            initView();
        });
    }

    public Expression getExpression() {
        return expression.get();
    }

    public ReadOnlyObjectProperty<Expression> expressionProperty() {
        return expression.getReadOnlyProperty();
    }
}