
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Cpu {

    @SerializedName("limit")
    @Expose
    private Long limit;
    @SerializedName("max_limit")
    @Expose
    private Long maxLimit;
    @SerializedName("mask")
    @Expose
    private String mask;
    @SerializedName("period")
    @Expose
    private Long period;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Cpu() {
    }

    /**
     * 
     * @param limit
     * @param mask
     * @param maxLimit
     * @param period
     */
    public Cpu(Long limit, Long maxLimit, String mask, Long period) {
        super();
        this.limit = limit;
        this.maxLimit = maxLimit;
        this.mask = mask;
        this.period = period;
    }

    public Long getLimit() {
        return limit;
    }

    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public Long getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(Long maxLimit) {
        this.maxLimit = maxLimit;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

}
