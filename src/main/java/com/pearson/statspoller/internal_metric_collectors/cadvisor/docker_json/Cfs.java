
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Cfs {

    @SerializedName("periods")
    @Expose
    private Long periods;
    @SerializedName("throttled_periods")
    @Expose
    private Long throttledPeriods;
    @SerializedName("throttled_time")
    @Expose
    private Long throttledTime;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Cfs() {
    }

    /**
     * 
     * @param periods
     * @param throttledPeriods
     * @param throttledTime
     */
    public Cfs(Long periods, Long throttledPeriods, Long throttledTime) {
        super();
        this.periods = periods;
        this.throttledPeriods = throttledPeriods;
        this.throttledTime = throttledTime;
    }

    public Long getPeriods() {
        return periods;
    }

    public void setPeriods(Long periods) {
        this.periods = periods;
    }

    public Long getThrottledPeriods() {
        return throttledPeriods;
    }

    public void setThrottledPeriods(Long throttledPeriods) {
        this.throttledPeriods = throttledPeriods;
    }

    public Long getThrottledTime() {
        return throttledTime;
    }

    public void setThrottledTime(Long throttledTime) {
        this.throttledTime = throttledTime;
    }

}
