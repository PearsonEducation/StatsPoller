
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TaskStats {

    @SerializedName("nr_sleeping")
    @Expose
    private Long nrSleeping;
    @SerializedName("nr_running")
    @Expose
    private Long nrRunning;
    @SerializedName("nr_stopped")
    @Expose
    private Long nrStopped;
    @SerializedName("nr_uninterruptible")
    @Expose
    private Long nrUninterruptible;
    @SerializedName("nr_io_wait")
    @Expose
    private Long nrIoWait;

    /**
     * No args constructor for use in serialization
     * 
     */
    public TaskStats() {
    }

    /**
     * 
     * @param nrStopped
     * @param nrUninterruptible
     * @param nrRunning
     * @param nrSleeping
     * @param nrIoWait
     */
    public TaskStats(Long nrSleeping, Long nrRunning, Long nrStopped, Long nrUninterruptible, Long nrIoWait) {
        super();
        this.nrSleeping = nrSleeping;
        this.nrRunning = nrRunning;
        this.nrStopped = nrStopped;
        this.nrUninterruptible = nrUninterruptible;
        this.nrIoWait = nrIoWait;
    }

    public Long getNrSleeping() {
        return nrSleeping;
    }

    public void setNrSleeping(Long nrSleeping) {
        this.nrSleeping = nrSleeping;
    }

    public Long getNrRunning() {
        return nrRunning;
    }

    public void setNrRunning(Long nrRunning) {
        this.nrRunning = nrRunning;
    }

    public Long getNrStopped() {
        return nrStopped;
    }

    public void setNrStopped(Long nrStopped) {
        this.nrStopped = nrStopped;
    }

    public Long getNrUninterruptible() {
        return nrUninterruptible;
    }

    public void setNrUninterruptible(Long nrUninterruptible) {
        this.nrUninterruptible = nrUninterruptible;
    }

    public Long getNrIoWait() {
        return nrIoWait;
    }

    public void setNrIoWait(Long nrIoWait) {
        this.nrIoWait = nrIoWait;
    }

}
