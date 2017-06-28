
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Cpu_ {

    @SerializedName("usage")
    @Expose
    private Usage usage;
    @SerializedName("cfs")
    @Expose
    private Cfs cfs;
    @SerializedName("load_average")
    @Expose
    private Long loadAverage;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Cpu_() {
    }

    /**
     * 
     * @param usage
     * @param loadAverage
     * @param cfs
     */
    public Cpu_(Usage usage, Cfs cfs, Long loadAverage) {
        super();
        this.usage = usage;
        this.cfs = cfs;
        this.loadAverage = loadAverage;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public Cfs getCfs() {
        return cfs;
    }

    public void setCfs(Cfs cfs) {
        this.cfs = cfs;
    }

    public Long getLoadAverage() {
        return loadAverage;
    }

    public void setLoadAverage(Long loadAverage) {
        this.loadAverage = loadAverage;
    }

}
