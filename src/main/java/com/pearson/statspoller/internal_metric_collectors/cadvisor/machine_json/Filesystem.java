
package com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Filesystem {

    @SerializedName("device")
    @Expose
    private String device;
    @SerializedName("capacity")
    @Expose
    private Long capacity;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("inodes")
    @Expose
    private Long inodes;
    @SerializedName("has_inodes")
    @Expose
    private Boolean hasInodes;

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Long getCapacity() {
        return capacity;
    }

    public void setCapacity(Long capacity) {
        this.capacity = capacity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getInodes() {
        return inodes;
    }

    public void setInodes(Long inodes) {
        this.inodes = inodes;
    }

    public Boolean getHasInodes() {
        return hasInodes;
    }

    public void setHasInodes(Boolean hasInodes) {
        this.hasInodes = hasInodes;
    }

}
