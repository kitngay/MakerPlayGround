package io.makerplayground.ui;

import io.makerplayground.project.Condition;
import io.makerplayground.project.Project;
import io.makerplayground.project.State;
import io.makerplayground.uihelper.DynamicViewModelCreator;

/**
 *
 * Created by tanyagorn on 6/13/2017.
 */
public class CanvasViewModel {
    protected final Project project;
    private final DynamicViewModelCreator<State, SceneViewModel> paneStateViewModel;
    private final DynamicViewModelCreator<Condition,LineViewModel> lineViewModel;

    public CanvasViewModel(Project project) {
        this.project = project;
        this.paneStateViewModel = new DynamicViewModelCreator<>(project.getState(), state -> new SceneViewModel(state, project));
        this.lineViewModel = new DynamicViewModelCreator<Condition, LineViewModel>(project.getCondition(),LineViewModel::new);
    }

    public DynamicViewModelCreator<Condition, LineViewModel> getLineViewModel() {
        return lineViewModel;
    }

    public DynamicViewModelCreator<State, SceneViewModel> getPaneStateViewModel() {
        return paneStateViewModel;
    }

    public Project getProject() {
        return project;
    }
}
