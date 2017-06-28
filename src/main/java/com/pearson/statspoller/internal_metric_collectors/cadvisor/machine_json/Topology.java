
package com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Topology {

    @SerializedName("node_id")
    @Expose
    private Long nodeId;
    @SerializedName("memory")
    @Expose
    private Long memory;
    @SerializedName("cores")
    @Expose
    private List<Core> cores = new ArrayList<Core>();

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public Long getMemory() {
        return memory;
    }

    public void setMemory(Long memory) {
        this.memory = memory;
    }

    public List<Core> getCores() {
        return cores;
    }

    public void setCores(List<Core> cores) {
        this.cores = cores;
    }

}
