package io.makerplayground.ui.canvas.chip;

import io.makerplayground.helper.NumberWithUnit;
import io.makerplayground.project.expression.Expression;
import io.makerplayground.project.term.NumberWithUnitTerm;
import io.makerplayground.project.term.OperatorTerm;
import io.makerplayground.project.term.StringTerm;
import io.makerplayground.project.term.Term;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;


import java.io.IOException;

public class ChipField extends HBox {
    private final Expression expression;

    @FXML
    private FlowPane mainPane;

    @FXML
    private Button backspaceBtn;

    public ChipField(Expression expression) {
        this.expression = expression;
        initView();
        initEvent();
    }

    private void initView() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/ChipField.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // initialize chip based on expression
        expression.getTerms().forEach(this::addTerm);

        setFocusTraversable(true);
    }

    private void initEvent() {
        // add/remove chip when expression changed
        expression.getTerms().addListener((ListChangeListener<? super Term>) c -> {
            while (c.next()) {
                if (c.wasPermutated()) {
                    throw new UnsupportedOperationException();
                } else if (c.wasUpdated()) {
                    throw new UnsupportedOperationException();
                } else {
                    for (Term removedItem : c.getRemoved()) {
                        removeTerm(removedItem, c.getFrom());
                    }
                    for (Term addedItem : c.getAddedSubList()) {
                        addTerm(addedItem, c.getFrom());
                    }
                }
            }
        });

        backspaceBtn.setOnMouseReleased(this::handleBackspace);
    }

    // Add new chip to the current cursor position of ChipField. Change will also be reflected to the underlying expression.
    public void addTerm(Term t) {
        expression.getTerms().add(t);
    }

    // Add new chip when underlying expression has changed
    private void addTerm(Term t, int index) {
        Chip chip = null;
        if (t instanceof NumberWithUnitTerm) {
            chip = new NumberWithUnitChip((NumberWithUnit) t.getValue());
        } else if (t instanceof StringTerm) {
            chip = new StringChip((String) t.getValue());
        } else if (t instanceof OperatorTerm) {
            chip = new OperatorChip((OperatorTerm.OP) t.getValue());
        } else {
            throw new IllegalStateException();
        }
        mainPane.getChildren().add(index, chip);
    }

    // Remove chip when underlying expression has changed
    private void removeTerm(Term t, int index) {
        mainPane.getChildren().remove(index);
    }

    private void handleBackspace(MouseEvent mouseEvent) {
        if (expression.getTerms().size() > 0) {
            expression.getTerms().remove(expression.getTerms().size() - 1);
        }
    }
}
