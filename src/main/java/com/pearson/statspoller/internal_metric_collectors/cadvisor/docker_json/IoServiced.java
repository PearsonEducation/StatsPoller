
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class IoServiced {

    @SerializedName("major")
    @Expose
    private Long major;
    @SerializedName("minor")
    @Expose
    private Long minor;
    @SerializedName("stats")
    @Expose
    private Stats_ stats;

    /**
     * No args constructor for use in serialization
     * 
     */
    public IoServiced() {
    }

    /**
     * 
     * @param minor
     * @param stats
     * @param major
     */
    public IoServiced(Long major, Long minor, Stats_ stats) {
        super();
        this.major = major;
        this.minor = minor;
        this.stats = stats;
    }

    public Long getMajor() {
        return major;
    }

    public void setMajor(Long major) {
        this.major = major;
    }

    public Long getMinor() {
        return minor;
    }

    public void setMinor(Long minor) {
        this.minor = minor;
    }

    public Stats_ getStats() {
        return stats;
    }

    public void setStats(Stats_ stats) {
        this.stats = stats;
    }

}
