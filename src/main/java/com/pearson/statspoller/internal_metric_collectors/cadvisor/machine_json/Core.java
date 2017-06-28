
package com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Core {

    @SerializedName("core_id")
    @Expose
    private Long coreId;
    @SerializedName("thread_ids")
    @Expose
    private List<Long> threadIds = new ArrayList<Long>();

    public Long getCoreId() {
        return coreId;
    }

    public void setCoreId(Long coreId) {
        this.coreId = coreId;
    }

    public List<Long> getThreadIds() {
        return threadIds;
    }

    public void setThreadIds(List<Long> threadIds) {
        this.threadIds = threadIds;
    }

}
