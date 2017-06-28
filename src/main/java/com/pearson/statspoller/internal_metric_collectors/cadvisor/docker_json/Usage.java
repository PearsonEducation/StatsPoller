
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Usage {

    @SerializedName("total")
    @Expose
    private Long total;
    @SerializedName("per_cpu_usage")
    @Expose
    private List<Long> perCpuUsage = new ArrayList<Long>();
    @SerializedName("user")
    @Expose
    private Long user;
    @SerializedName("system")
    @Expose
    private Long system;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Usage() {
    }

    /**
     * 
     * @param total
     * @param system
     * @param perCpuUsage
     * @param user
     */
    public Usage(Long total, List<Long> perCpuUsage, Long user, Long system) {
        super();
        this.total = total;
        this.perCpuUsage = perCpuUsage;
        this.user = user;
        this.system = system;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<Long> getPerCpuUsage() {
        return perCpuUsage;
    }

    public void setPerCpuUsage(List<Long> perCpuUsage) {
        this.perCpuUsage = perCpuUsage;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }

    public Long getSystem() {
        return system;
    }

    public void setSystem(Long system) {
        this.system = system;
    }

}
