package io.makerplayground.project;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.makerplayground.device.Device;
import io.makerplayground.device.GenericDevice;
import io.makerplayground.helper.Peripheral;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by Nuntipat Narkthong on 6/2/2017 AD.
 */
@JsonSerialize (using = ProjectDeviceSerializer.class)
public class ProjectDevice {
    private String name;
    private final GenericDevice genericDevice;
    private boolean autoSelectDevice;
    private Device actualDevice;
    private Map<Peripheral, Peripheral> deviceConnection; // connection from this device (key) to the processor (value)
    private Device dependentDevice;
    private Map<Peripheral, Peripheral> dependentDeviceConnection; // connection from this device (key) to the processor (value)

    public ProjectDevice(String name, GenericDevice genericDevice) {
        this.name = name;
        this.genericDevice = genericDevice;
        this.autoSelectDevice = true;
        this.actualDevice = null;
        this.deviceConnection = new HashMap<>();
        this.dependentDevice = null;
        this.dependentDeviceConnection = new HashMap<>();
    }

    ProjectDevice(String name, GenericDevice genericDevice, boolean autoSelectDevice, Device actualDevice, Map<Peripheral, Peripheral> deviceConnection, Device dependentDevice, Map<Peripheral, Peripheral> dependentDeviceConnection) {
        this.name = name;
        this.genericDevice = genericDevice;
        this.autoSelectDevice = autoSelectDevice;
        this.actualDevice = actualDevice;
        this.deviceConnection = deviceConnection;
        this.dependentDevice = dependentDevice;
        this.dependentDeviceConnection = dependentDeviceConnection;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GenericDevice getGenericDevice() {
        return genericDevice;
    }

    public boolean isAutoSelectDevice() {
        return autoSelectDevice;
    }

    public void setAutoSelectDevice(boolean autoSelectDevice) {
        this.autoSelectDevice = autoSelectDevice;
    }

    public Device getActualDevice() {
        return actualDevice;
    }

    public void setActualDevice(Device actualDevice) {
        this.actualDevice = actualDevice;
    }

    public Map<Peripheral, Peripheral> getDeviceConnection() {
        return deviceConnection;
    }

//    public void setDeviceConnection(Map<Peripheral, Peripheral> deviceConnection) {
//        this.deviceConnection = deviceConnection;
//    }
    public void setDeviceConnection(Peripheral device, Peripheral processor) {
        this.deviceConnection.put(device, processor);
    }

    public void removeDeviceConnection(Peripheral device) {
        this.deviceConnection.remove(device);
    }

    public Device getDependentDevice() {
        return dependentDevice;
    }

    public void setDependentDevice(Device dependentDevice) {
        this.dependentDevice = dependentDevice;
    }

    public Map<Peripheral, Peripheral> getDependentDeviceConnection() {
        return dependentDeviceConnection;
    }

    public void setDependentDeviceConnection(Peripheral device, Peripheral processor) {
        this.dependentDeviceConnection.put(device, processor);
    }

//    public void setDependentDeviceConnection(Map<Peripheral, Peripheral> dependentDeviceConnection) {
//        this.dependentDeviceConnection = dependentDeviceConnection;
//    }
}
