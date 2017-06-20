package io.makerplayground.project;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Created by tanyagorn on 6/2/2017 AD.
 */
public class State {

    public enum DelayUnit {MilliSecond, Second};

    private final StringProperty name;
    private final ObservableList<UserSetting> setting;
    private final Point position;
    private final SimpleDoubleProperty width, height;
    private final SimpleDoubleProperty delay;
    private final DelayUnit delayUnit;

    private final Point sourcePort;
    private final Point destPort;

    State() {
        this.name = new SimpleStringProperty("");
        this.setting = FXCollections.observableArrayList();
        this.position = new Point(20, 20);
        this.width = new SimpleDoubleProperty(200);
        this.height = new SimpleDoubleProperty(300);
        this.delay = new SimpleDoubleProperty(0);
        this.delayUnit = DelayUnit.Second;

        this.sourcePort = new Point(0, 0);
        this.sourcePort.xProperty().bind(position.xProperty());
        this.sourcePort.yProperty().bind(position.yProperty().add(height.divide(2)));
        this.destPort = new Point(0, 0);
        this.destPort.xProperty().bind(position.xProperty().add(width));
        this.destPort.yProperty().bind(position.yProperty().add(height.divide(2)));
    }

    void addDevice(ProjectDevice device) {
        setting.add(new UserSetting(device));
    }

    void removeDevice(ProjectDevice device) {
        for (int i = setting.size() - 1; i >= 0; i--) {
            if (setting.get(i).getDevice() == device) {
                setting.remove(i);
            }
        }
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public Point getPosition() {
        return position;
    }

    public double getWidth() {
        return width.get();
    }

    public SimpleDoubleProperty widthProperty() {
        return width;
    }

    public void setWidth(double width) {
        this.width.set(width);
    }

    public double getHeight() {
        return height.get();
    }

    public SimpleDoubleProperty heightProperty() {
        return height;
    }

    public void setHeight(double height) {
        this.height.set(height);
    }

    public double getDelay() {
        return delay.get();
    }

    public SimpleDoubleProperty delayProperty() {
        return delay;
    }

    public void setDelay(double delay) {
        this.delay.set(delay);
    }

    public DelayUnit getDelayUnit() {
        return delayUnit;
    }

    public ObservableList<UserSetting> getSetting() {
        return setting;
    }

    public Point getSourcePort() {
        return sourcePort;
    }

    public Point getDestPort() {
        return destPort;
    }

}
