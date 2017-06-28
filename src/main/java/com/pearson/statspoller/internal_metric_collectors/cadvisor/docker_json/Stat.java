
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Stat {

    @SerializedName("timestamp")
    @Expose
    private String timestamp;
    @SerializedName("cpu")
    @Expose
    private Cpu_ cpu;
    @SerializedName("diskio")
    @Expose
    private Diskio diskio;
    @SerializedName("memory")
    @Expose
    private Memory_ memory;
    @SerializedName("network")
    @Expose
    private Network network;
    @SerializedName("filesystem")
    @Expose
    private List<Filesystem> filesystem = new ArrayList<Filesystem>();
    @SerializedName("task_stats")
    @Expose
    private TaskStats taskStats;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Stat() {
    }

    /**
     * 
     * @param timestamp
     * @param cpu
     * @param diskio
     * @param taskStats
     * @param filesystem
     * @param network
     * @param memory
     */
    public Stat(String timestamp, Cpu_ cpu, Diskio diskio, Memory_ memory, Network network, List<Filesystem> filesystem, TaskStats taskStats) {
        super();
        this.timestamp = timestamp;
        this.cpu = cpu;
        this.diskio = diskio;
        this.memory = memory;
        this.network = network;
        this.filesystem = filesystem;
        this.taskStats = taskStats;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Cpu_ getCpu() {
        return cpu;
    }

    public void setCpu(Cpu_ cpu) {
        this.cpu = cpu;
    }

    public Diskio getDiskio() {
        return diskio;
    }

    public void setDiskio(Diskio diskio) {
        this.diskio = diskio;
    }

    public Memory_ getMemory() {
        return memory;
    }

    public void setMemory(Memory_ memory) {
        this.memory = memory;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public List<Filesystem> getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(List<Filesystem> filesystem) {
        this.filesystem = filesystem;
    }

    public TaskStats getTaskStats() {
        return taskStats;
    }

    public void setTaskStats(TaskStats taskStats) {
        this.taskStats = taskStats;
    }

}
