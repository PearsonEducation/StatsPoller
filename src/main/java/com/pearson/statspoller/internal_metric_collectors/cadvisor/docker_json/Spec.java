
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Spec {

    @SerializedName("creation_time")
    @Expose
    private String creationTime;
    @SerializedName("has_cpu")
    @Expose
    private Boolean hasCpu;
    @SerializedName("cpu")
    @Expose
    private Cpu cpu;
    @SerializedName("has_memory")
    @Expose
    private Boolean hasMemory;
    @SerializedName("memory")
    @Expose
    private Memory memory;
    @SerializedName("has_network")
    @Expose
    private Boolean hasNetwork;
    @SerializedName("has_filesystem")
    @Expose
    private Boolean hasFilesystem;
    @SerializedName("has_diskio")
    @Expose
    private Boolean hasDiskio;
    @SerializedName("has_custom_metrics")
    @Expose
    private Boolean hasCustomMetrics;
    @SerializedName("image")
    @Expose
    private String image;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Spec() {
    }

    /**
     * 
     * @param hasCpu
     * @param hasNetwork
     * @param hasFilesystem
     * @param hasMemory
     * @param cpu
     * @param image
     * @param hasDiskio
     * @param creationTime
     * @param hasCustomMetrics
     * @param memory
     */
    public Spec(String creationTime, Boolean hasCpu, Cpu cpu, Boolean hasMemory, Memory memory, Boolean hasNetwork, Boolean hasFilesystem, Boolean hasDiskio, Boolean hasCustomMetrics, String image) {
        super();
        this.creationTime = creationTime;
        this.hasCpu = hasCpu;
        this.cpu = cpu;
        this.hasMemory = hasMemory;
        this.memory = memory;
        this.hasNetwork = hasNetwork;
        this.hasFilesystem = hasFilesystem;
        this.hasDiskio = hasDiskio;
        this.hasCustomMetrics = hasCustomMetrics;
        this.image = image;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public Boolean getHasCpu() {
        return hasCpu;
    }

    public void setHasCpu(Boolean hasCpu) {
        this.hasCpu = hasCpu;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public void setCpu(Cpu cpu) {
        this.cpu = cpu;
    }

    public Boolean getHasMemory() {
        return hasMemory;
    }

    public void setHasMemory(Boolean hasMemory) {
        this.hasMemory = hasMemory;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Boolean getHasNetwork() {
        return hasNetwork;
    }

    public void setHasNetwork(Boolean hasNetwork) {
        this.hasNetwork = hasNetwork;
    }

    public Boolean getHasFilesystem() {
        return hasFilesystem;
    }

    public void setHasFilesystem(Boolean hasFilesystem) {
        this.hasFilesystem = hasFilesystem;
    }

    public Boolean getHasDiskio() {
        return hasDiskio;
    }

    public void setHasDiskio(Boolean hasDiskio) {
        this.hasDiskio = hasDiskio;
    }

    public Boolean getHasCustomMetrics() {
        return hasCustomMetrics;
    }

    public void setHasCustomMetrics(Boolean hasCustomMetrics) {
        this.hasCustomMetrics = hasCustomMetrics;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

}
