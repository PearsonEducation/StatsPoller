
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Memory_ {

    @SerializedName("usage")
    @Expose
    private Long usage;
    @SerializedName("cache")
    @Expose
    private Long cache;
    @SerializedName("rss")
    @Expose
    private Long rss;
    @SerializedName("swap")
    @Expose
    private Long swap;
    @SerializedName("working_set")
    @Expose
    private Long workingSet;
    @SerializedName("failcnt")
    @Expose
    private Long failcnt;
    @SerializedName("container_data")
    @Expose
    private ContainerData containerData;
    @SerializedName("hierarchical_data")
    @Expose
    private HierarchicalData hierarchicalData;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Memory_() {
    }

    /**
     * 
     * @param failcnt
     * @param hierarchicalData
     * @param cache
     * @param rss
     * @param usage
     * @param workingSet
     * @param containerData
     * @param swap
     */
    public Memory_(Long usage, Long cache, Long rss, Long swap, Long workingSet, Long failcnt, ContainerData containerData, HierarchicalData hierarchicalData) {
        super();
        this.usage = usage;
        this.cache = cache;
        this.rss = rss;
        this.swap = swap;
        this.workingSet = workingSet;
        this.failcnt = failcnt;
        this.containerData = containerData;
        this.hierarchicalData = hierarchicalData;
    }

    public Long getUsage() {
        return usage;
    }

    public void setUsage(Long usage) {
        this.usage = usage;
    }

    public Long getCache() {
        return cache;
    }

    public void setCache(Long cache) {
        this.cache = cache;
    }

    public Long getRss() {
        return rss;
    }

    public void setRss(Long rss) {
        this.rss = rss;
    }

    public Long getSwap() {
        return swap;
    }

    public void setSwap(Long swap) {
        this.swap = swap;
    }

    public Long getWorkingSet() {
        return workingSet;
    }

    public void setWorkingSet(Long workingSet) {
        this.workingSet = workingSet;
    }

    public Long getFailcnt() {
        return failcnt;
    }

    public void setFailcnt(Long failcnt) {
        this.failcnt = failcnt;
    }

    public ContainerData getContainerData() {
        return containerData;
    }

    public void setContainerData(ContainerData containerData) {
        this.containerData = containerData;
    }

    public HierarchicalData getHierarchicalData() {
        return hierarchicalData;
    }

    public void setHierarchicalData(HierarchicalData hierarchicalData) {
        this.hierarchicalData = hierarchicalData;
    }

}
