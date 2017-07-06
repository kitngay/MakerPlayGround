package io.makerplayground.ui.canvas;

import io.makerplayground.project.*;
import io.makerplayground.uihelper.DynamicViewModelCreator;
import io.makerplayground.uihelper.ViewModelFactory;

/**
 *
 * Created by tanyagorn on 6/13/2017.
 */
public class CanvasViewModel {
    protected final Project project;
    private final DynamicViewModelCreator<Scene, SceneViewModel> paneStateViewModel;
    private final DynamicViewModelCreator<Condition, ConditionViewModel> conditionViewModel;
    private final DynamicViewModelCreator<Line,LineViewModel> lineViewModel;

    public CanvasViewModel(Project project) {
        this.project = project;
        this.paneStateViewModel = new DynamicViewModelCreator<>(project.getScene(), scene -> new SceneViewModel(scene, project));
        this.conditionViewModel = new DynamicViewModelCreator<>(project.getCondition(), condition -> new ConditionViewModel(condition, project));
        this.lineViewModel = new DynamicViewModelCreator<>(project.getLine(), LineViewModel::new);
    }

    public void connectState(NodeElement nodeElement1, NodeElement nodeElement2) {
        project.addLine(nodeElement1, nodeElement2);
    }

    public DynamicViewModelCreator<Line, LineViewModel> getLineViewModel() {
        return lineViewModel;
    }

    public DynamicViewModelCreator<Condition, ConditionViewModel> getConditionViewModel() {
        return conditionViewModel;
    }

    public DynamicViewModelCreator<Scene, SceneViewModel> getPaneStateViewModel() {
        return paneStateViewModel;
    }

    public Project getProject() {
        return project;
    }
}
