package io.makerplayground.ui.canvas;

import io.makerplayground.ui.canvas.event.InteractiveNodeEvent;
import io.makerplayground.uihelper.DynamicViewCreator;
import io.makerplayground.uihelper.DynamicViewCreatorBuilder;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;

/**
 *
 */
public class CanvasView extends AnchorPane {
    private final InteractivePane mainPane = new InteractivePane();
    private final Button addStateBtn = new Button();
    private final Button addConditionBtn = new Button();
    private final TextField zoomTextField = new TextField();
    private final Button zoomInButton = new Button();
    private final Button zoomOutButton = new Button();
    private final Button zoomDefaultButton = new Button();

    private final CanvasViewModel canvasViewModel;

    public CanvasView(CanvasViewModel canvasViewModel) {
        this.canvasViewModel = canvasViewModel;
        initView();
        initEvent();
    }

    private void initView() {
        getStylesheets().add(getClass().getResource("/css/CanvasView.css").toExternalForm());

        addStateBtn.setText("+ Add Scene");
        addStateBtn.setId("addStateBtn");
        addStateBtn.setOnAction(event -> canvasViewModel.project.addState());
        AnchorPane.setTopAnchor(addStateBtn, 20.0);
        AnchorPane.setRightAnchor(addStateBtn, 20.0);

        addConditionBtn.setText("+ Add Condition");
        addConditionBtn.setId("addConditionBtn");
        addConditionBtn.setOnAction(event -> canvasViewModel.project.addCondition());
        AnchorPane.setTopAnchor(addConditionBtn, 65.0);
        AnchorPane.setRightAnchor(addConditionBtn, 20.0);

        zoomDefaultButton.setId("zoomDefaultButton");
        zoomDefaultButton.setMinSize(25,25);
        zoomDefaultButton.setOnAction(event -> mainPane.setScale(1));
        AnchorPane.setBottomAnchor(zoomDefaultButton, 20.0);
        AnchorPane.setRightAnchor(zoomDefaultButton, 125.0);

        zoomInButton.setId("zoomInButton");
        zoomInButton.setMinSize(25,25);
        zoomInButton.setOnAction(event -> {
            if(mainPane.getScale()< 5)
                mainPane.setScale(mainPane.getScale() + 0.1);
        });
        AnchorPane.setBottomAnchor(zoomInButton, 20.0);
        AnchorPane.setRightAnchor(zoomInButton, 20.0);

        zoomOutButton.setId("zoomOutButton");
        zoomOutButton.setMinSize(25,25);
        zoomOutButton.setOnAction(event -> {
            if(mainPane.getScale()> 0.5)
                mainPane.setScale(mainPane.getScale() - 0.1);
        });
        AnchorPane.setBottomAnchor(zoomOutButton, 20.0);
        AnchorPane.setRightAnchor(zoomOutButton, 95.0);

        zoomTextField.setText(String.valueOf(mainPane.getScale()));
        zoomTextField.setPrefWidth(40.0);
        zoomTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (!newValue.isEmpty()) {
                    double scale = Double.parseDouble(newValue);
                    if (scale > 0) {
                        mainPane.setScale(scale);
                    }
                }
            } catch (NumberFormatException e) {
                zoomTextField.setText(oldValue);
            }
        });
        AnchorPane.setBottomAnchor(zoomTextField, 20.0);
        AnchorPane.setRightAnchor(zoomTextField, 50.0);

        BeginSceneView beginSceneView = new BeginSceneView(canvasViewModel.getBeginViewModel(), mainPane);
        addConnectionEvent(beginSceneView);
        mainPane.addChildren(beginSceneView);

        DynamicViewCreator<InteractivePane, SceneViewModel, SceneView> canvasViewCreator =
                new DynamicViewCreatorBuilder<InteractivePane, SceneViewModel, SceneView>()
                        .setParent(mainPane)
                        .setModelLoader(canvasViewModel.getPaneStateViewModel())
                        .setViewFactory(sceneViewModel -> {
                            SceneView sceneView = new SceneView(sceneViewModel, mainPane);
                            addConnectionEvent(sceneView);
                            sceneView.addEventHandler(InteractiveNodeEvent.REMOVED, event -> canvasViewModel.project.removeState(sceneViewModel.getScene()));
                            return sceneView;
                        })
                        .setNodeAdder(InteractivePane::addChildren)
                        .setNodeRemover(InteractivePane::removeChildren)
                        .createDynamicViewCreator();
        DynamicViewCreator<InteractivePane, ConditionViewModel, ConditionView> conditionViewCreator =
                new DynamicViewCreatorBuilder<InteractivePane, ConditionViewModel, ConditionView>()
                        .setParent(mainPane)
                        .setModelLoader(canvasViewModel.getConditionViewModel())
                        .setViewFactory(conditionViewModel -> {
                            ConditionView conditionView = new ConditionView(conditionViewModel, mainPane);
                            addConnectionEvent(conditionView);
                            conditionView.addEventHandler(InteractiveNodeEvent.REMOVED, event -> canvasViewModel.project.removeCondition(conditionViewModel.getCondition()));
                            return conditionView;
                        })
                        .setNodeAdder(InteractivePane::addChildren)
                        .setNodeRemover(InteractivePane::removeChildren)
                        .createDynamicViewCreator();
        DynamicViewCreator<InteractivePane, LineViewModel, LineView> lineViewCreator =
                new DynamicViewCreatorBuilder<InteractivePane, LineViewModel, LineView>()
                        .setParent(mainPane)
                        .setModelLoader(canvasViewModel.getLineViewModel())
                        .setViewFactory(lineViewModel -> {
                            LineView lineView = new LineView(lineViewModel, mainPane);
                            lineView.addEventHandler(InteractiveNodeEvent.REMOVED, event -> canvasViewModel.project.removeLine(lineViewModel.getLine()));
                            return lineView;
                        })
                        .setNodeAdder((parent, node) -> {
                            parent.addChildren(node);
                            node.toBack();  // draw line below other elements so that it won't block mouse event on in/out port
                        })
                        .setNodeRemover(InteractivePane::removeChildren)
                        .createDynamicViewCreator();

        AnchorPane.setTopAnchor(mainPane, 0.0);
        AnchorPane.setRightAnchor(mainPane, 0.0);
        AnchorPane.setBottomAnchor(mainPane, 0.0);
        AnchorPane.setLeftAnchor(mainPane, 0.0);
        getChildren().addAll(mainPane, addStateBtn, addConditionBtn, zoomTextField, zoomInButton, zoomOutButton, zoomDefaultButton);
    }

    private void initEvent() {
        // allow node to be deleted using the delete key
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                for (InteractiveNode interactiveNode : mainPane.getSelectedNode()) {
                    if (interactiveNode instanceof SceneView) {
                        canvasViewModel.project.removeState(((SceneView) interactiveNode).getSceneViewModel().getScene());
                    } else if (interactiveNode instanceof ConditionView) {
                        canvasViewModel.project.removeCondition(((ConditionView) interactiveNode).getConditionViewModel().getCondition());
                    } else if (interactiveNode instanceof LineView) {
                        canvasViewModel.project.removeLine(((LineView) interactiveNode).getLineViewModel().getLine());
                    } else if (interactiveNode instanceof BeginSceneView) {
                        // we shouldn't delete begin from the canvas
                    } else {
                        throw new IllegalStateException("Found invalid object in the canvas!!!");
                    }
                }
            }
        });
    }

    private void addConnectionEvent(InteractiveNode node) {
        node.addEventFilter(InteractiveNodeEvent.CONNECTION_DONE, event ->
            canvasViewModel.connect(event.getSourceNode(), event.getDestinationNode())
        );
    }
}
